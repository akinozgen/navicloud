// NaviCloud SMTC yardımcısı — Windows System Media Transport Controls entegrasyonu.
// Gizli bir pencere üzerinden SMTC alır; JVM (masaüstü uygulama) ile stdin/stdout
// üzerinden konuşur:
//   JVM -> helper (stdin, satır bazlı, TAB ayraçlı):
//     M\t<başlık>\t<sanatçı>\t<albüm>\t<kapak-yolu>   (metadata güncelle)
//     S\t<playing|paused|stopped>                       (oynatma durumu)
//   helper -> JVM (stdout): PLAY | PAUSE | NEXT | PREV | STOP
// SMTC ayrıca donanım medya tuşlarını da bu uygulamaya yönlendirir.
#![windows_subsystem = "windows"]

use std::cell::RefCell;
use std::collections::VecDeque;
use std::io::{BufRead, Write};
use std::sync::atomic::{AtomicIsize, Ordering};
use std::sync::Mutex;

use windows::core::{factory, Result, HSTRING, PCWSTR};
use windows::Foundation::TypedEventHandler;
use windows::Media::{
    MediaPlaybackStatus, MediaPlaybackType, SystemMediaTransportControls,
    SystemMediaTransportControlsButton, SystemMediaTransportControlsButtonPressedEventArgs,
};
use windows::Storage::Streams::RandomAccessStreamReference;
use windows::Storage::StorageFile;
use windows::Win32::Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, WPARAM};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::System::WinRT::{
    ISystemMediaTransportControlsInterop, RoInitialize, RO_INIT_SINGLETHREADED,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DispatchMessageW, GetMessageW, PostMessageW, PostQuitMessage,
    RegisterClassW, TranslateMessage, CW_USEDEFAULT, MSG, WINDOW_EX_STYLE, WM_APP, WM_DESTROY,
    WM_QUIT, WNDCLASSW, WS_OVERLAPPEDWINDOW,
};

static QUEUE: Mutex<VecDeque<String>> = Mutex::new(VecDeque::new());
static HWND_RAW: AtomicIsize = AtomicIsize::new(0);
const WM_SMTC_CMD: u32 = WM_APP + 1;

thread_local! {
    static SMTC: RefCell<Option<SystemMediaTransportControls>> = const { RefCell::new(None) };
}

fn main() -> Result<()> {
    unsafe { RoInitialize(RO_INIT_SINGLETHREADED)? };

    let hinstance = unsafe { GetModuleHandleW(None)? };
    let class_name = HSTRING::from("NaviCloudSmtcWnd");
    let wc = WNDCLASSW {
        lpfnWndProc: Some(wndproc),
        hInstance: hinstance.into(),
        lpszClassName: PCWSTR(class_name.as_ptr()),
        ..Default::default()
    };
    unsafe { RegisterClassW(&wc) };

    // Gizli üst düzey pencere (asla gösterilmez) — SMTC bir HWND ister.
    let hwnd = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE::default(),
            &class_name,
            &HSTRING::from("NaviCloud"),
            WS_OVERLAPPEDWINDOW,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            None,
            None,
            HINSTANCE::from(hinstance),
            None,
        )?
    };
    HWND_RAW.store(hwnd.0 as isize, Ordering::SeqCst);

    let interop: ISystemMediaTransportControlsInterop =
        factory::<SystemMediaTransportControls, ISystemMediaTransportControlsInterop>()?;
    let smtc: SystemMediaTransportControls = unsafe { interop.GetForWindow(hwnd)? };

    smtc.SetIsEnabled(true)?;
    smtc.SetIsPlayEnabled(true)?;
    smtc.SetIsPauseEnabled(true)?;
    smtc.SetIsNextEnabled(true)?;
    smtc.SetIsPreviousEnabled(true)?;
    smtc.SetIsStopEnabled(true)?;
    smtc.SetPlaybackStatus(MediaPlaybackStatus::Closed)?;

    smtc.ButtonPressed(&TypedEventHandler::<
        SystemMediaTransportControls,
        SystemMediaTransportControlsButtonPressedEventArgs,
    >::new(move |_sender, args| {
        if let Some(args) = args.as_ref() {
            let name = match args.Button()? {
                SystemMediaTransportControlsButton::Play => "PLAY",
                SystemMediaTransportControlsButton::Pause => "PAUSE",
                SystemMediaTransportControlsButton::Next => "NEXT",
                SystemMediaTransportControlsButton::Previous => "PREV",
                SystemMediaTransportControlsButton::Stop => "STOP",
                _ => return Ok(()),
            };
            let mut out = std::io::stdout().lock();
            let _ = writeln!(out, "{name}");
            let _ = out.flush();
        }
        Ok(())
    }))?;

    SMTC.with(|s| *s.borrow_mut() = Some(smtc));

    // stdin okuyucu: satırları kuyruğa koyup pencereyi uyandırır.
    std::thread::spawn(|| {
        let stdin = std::io::stdin();
        for line in stdin.lock().lines() {
            match line {
                Ok(l) => {
                    QUEUE.lock().unwrap().push_back(l);
                    post(WM_SMTC_CMD);
                }
                Err(_) => break,
            }
        }
        post(WM_QUIT); // stdin kapandı → çık
    });

    let mut msg = MSG::default();
    unsafe {
        while GetMessageW(&mut msg, None, 0, 0).as_bool() {
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
    Ok(())
}

fn post(msg: u32) {
    let hwnd = HWND(HWND_RAW.load(Ordering::SeqCst) as _);
    unsafe {
        let _ = PostMessageW(hwnd, msg, WPARAM(0), LPARAM(0));
    }
}

extern "system" fn wndproc(hwnd: HWND, msg: u32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    match msg {
        WM_SMTC_CMD => {
            drain_commands();
            LRESULT(0)
        }
        WM_DESTROY => {
            unsafe { PostQuitMessage(0) };
            LRESULT(0)
        }
        _ => unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) },
    }
}

fn drain_commands() {
    let cmds: Vec<String> = {
        let mut q = QUEUE.lock().unwrap();
        q.drain(..).collect()
    };
    SMTC.with(|s| {
        if let Some(smtc) = s.borrow().as_ref() {
            for cmd in cmds {
                match apply(smtc, &cmd) {
                    Ok(_) => eprintln!("APPLIED {cmd:?}"),
                    Err(e) => eprintln!("APPLY_ERR {cmd:?}: {e:?}"),
                }
                let _ = std::io::stderr().flush();
            }
        } else {
            eprintln!("NO_SMTC");
        }
    });
}

fn apply(smtc: &SystemMediaTransportControls, line: &str) -> Result<()> {
    // Bazı yazarlar akış başına BOM ekler (ör. PowerShell); tag eşleşsin diye kırp.
    let line = line.trim_start_matches('\u{feff}');
    let mut parts = line.split('\t');
    match parts.next() {
        Some("M") => {
            let title = parts.next().unwrap_or("");
            let artist = parts.next().unwrap_or("");
            let album = parts.next().unwrap_or("");
            let cover = parts.next().unwrap_or("");
            let du = smtc.DisplayUpdater()?;
            du.SetType(MediaPlaybackType::Music)?;
            let mp = du.MusicProperties()?;
            mp.SetTitle(&HSTRING::from(title))?;
            mp.SetArtist(&HSTRING::from(artist))?;
            mp.SetAlbumTitle(&HSTRING::from(album))?;
            if !cover.is_empty() {
                if let Ok(file) = StorageFile::GetFileFromPathAsync(&HSTRING::from(cover))?.get() {
                    if let Ok(stream) = RandomAccessStreamReference::CreateFromFile(&file) {
                        du.SetThumbnail(&stream)?;
                    }
                }
            }
            du.Update()?;
        }
        Some("S") => {
            let status = match parts.next().unwrap_or("") {
                "playing" => MediaPlaybackStatus::Playing,
                "paused" => MediaPlaybackStatus::Paused,
                "stopped" => MediaPlaybackStatus::Stopped,
                _ => MediaPlaybackStatus::Closed,
            };
            smtc.SetPlaybackStatus(status)?;
        }
        _ => {}
    }
    Ok(())
}
