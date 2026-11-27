# AppFunctions 없이 원본 Android 앱 시험하기

목표는 사용자가 열어 둔 상용 앱에서 Hyperchat으로 보이는 항목을 읽고, 한 번의 승인을 거쳐 그 앱의 다른 화면에 도달하는지 검증하는 것이다. 재현 환경은 영어 UI의 Android 16(API 36) 전용 에뮬레이터다. 원본 앱 설치를 마친 후 비행기 모드를 켜고 Wi-Fi와 이동통신을 끈다. Hyperchat에 로컬 모델을 가져올 필요가 없다.

## 시험 대상과 출처

2026-11-26 확인한 Google Play의 누적 다운로드 구간은 VLC 5억+, AIMP 5천만+, Total Commander와 AnkiDroid 각 1천만+다. 아래 표의 Play 링크에서 확인할 수 있다.

| 대상 | 고정한 배포본 | 선정 이유 |
| --- | --- | --- |
| VLC for Android | 3.7.2 · `org.videolan.vlc` · x86_64 | 계정 없이 미디어 라이브러리 탭을 이동할 수 있다. [배포 디렉터리](https://download.videolan.org/pub/videolan/vlc-android/3.7.2/) · [Google Play](https://play.google.com/store/apps/details?id=org.videolan.vlc) |
| Total Commander | 3.62d · `com.ghisler.android.TotalCommander` · universal | 파일 관리자의 메뉴를 파일 복사·삭제와 독립적으로 시험할 수 있다. 개발자가 운영하는 F-Droid 저장소의 빌드를 사용했다. [공식 배포 안내](https://www.ghisler.com/fdroid.htm) · [고정한 배포본](https://github.com/chrisgch/tca/tree/e222aa630730a9282042f21f7d757a68a929df74/fdroid/repo) · [Google Play](https://play.google.com/store/apps/details?id=com.ghisler.android.TotalCommander) |
| AIMP | 4.31.1751 · `com.aimp.player` · 일반 Android 빌드 | 로컬 음악 플레이어에서 고유 디자인의 컨트롤을 접근성 서비스가 어느 범위까지 읽을 수 있는지 확인한다. [공식 배포처](https://aimp.ru/?do=download&os=android) · [Google Play](https://play.google.com/store/apps/details?id=com.aimp.player) |
| AnkiDroid | 2.25.3 · `com.ichi2.anki` · 공식 릴리스의 `play-universal` 변형 | 로컬 플래시카드 앱이다. 같은 버전의 GitHub x86_64 변형은 온보딩에서 모든 파일 접근을 요구한다. Play용 변형은 이 권한을 선언하지 않는다. [공식 릴리스 및 해시](https://github.com/ankidroid/Anki-Android/releases/tag/v2.25.3) · [Google Play](https://play.google.com/store/apps/details?id=com.ichi2.anki) |

네 앱의 배포본 5개 모두 서명 검증을 통과했고, 각각의 manifest에서 AppFunction 서비스·바인딩·메타데이터 관련 표식은 0건이었다. 모두 `INTERNET` 및 `ACCESS_NETWORK_STATE`를 선언한다. 이 문서에서 오프라인은 시험 동안 활성 기본 네트워크가 없는 상태를 뜻한다. 설치 전 선언 권한 수는 VLC 31개, Total Commander 22개, AIMP 18개, AnkiDroid Play용 15개·GitHub x86_64용 17개다. 특정 버전의 정적 관찰이므로 이후 업데이트 때 다시 검사한다. [Android AppFunctions](https://developer.android.com/ai/appfunctions)

| 파일 | SHA-256 |
| --- | --- |
| VLC 3.7.2 x86_64 | `7164a458c07daf4c0f4f0ca7d9367fdff329c47c5a30e34e7c077f8d43570a26` |
| Total Commander 3.62d universal | `d918ccc0506f6ac3b72fb2cc4dc1eccb3f83eb873077d982a79054d8d65467e4` |
| AIMP 4.31.1751 | `b129d5b12b56f9ffe91ac9f9f157ca12df4b1e80d94e8bb69b2eb0855b7f1875` |
| AnkiDroid 2.25.3 x86_64 | `154101f06f4ba286248fa4a26863f759d3a1631bd4b1d6790503ec35a85edac7` |
| AnkiDroid 2.25.3 play-universal | `0f58e1b86075e22c116af1d673a44c620014e4ccc953a157eba49fb5b46a8c27` |

이 API 36 이미지의 `dumpsys app_function`은 사용자 0의 헤더만 반환했다. `cmd app_function`에는 셸 명령 구현이 없어 플랫폼의 패키지별 함수 개수는 확인할 수 없었다. 아래 실험은 Hyperchat 접근성 서비스만 사용한다. [플랫폼 색인 확인 방법](https://developer.android.com/ai/appfunctions/add-appfunctions)

## 시작 화면과 판정

| 시험 | 준비와 명령 | 원본 앱에서 확인할 결과 |
| --- | --- | --- |
| VLC 오디오 라이브러리 | 처음 실행하면 온보딩을 건너뛰고 알림을 거절한다. **Video** 탭에서 `/controls`를 보낸 뒤 `Open Audio`를 보내고 승인한다. | 읽기 요청 후에는 Video가 유지된다. 승인 후 Audio 탭에 **ARTISTS**가 나타난다. 미디어 파일이 없어도 재현할 수 있다. |
| VLC 승인 취소 | 같은 Video 화면에서 `Open Audio`를 보내 승인 버튼을 띄운 뒤 입력 내용을 `계산기 앱 열어줘`로 바꾼다. | 이전 승인 버튼이 사라진다. 변경된 요청을 보내면 지원 범위 설명이 뜨고 VLC Video 화면을 유지한다. |
| Total Commander 파일 찾기 | 파일 관리자의 홈에서 검색을 여는 **Search...** 컨트롤이 보이는 상태로 `Open Search...`를 보내고 승인한다. | 원본 앱에서 **Find files** 검색 창을 연다. 검색어 입력·검색 실행·파일 복사나 삭제는 이 시험 범위에 포함되지 않는다. |
| Total Commander 파일명 입력 | 위 검색 창에서 파일명 칸을 탭하고 `Type hyperchat-demo.txt`를 보낸다. 별도로 승인한다. | 승인 전 원래 값이 유지되고 승인 후 대상 앱의 파일명 칸 값이 정확히 `hyperchat-demo.txt`가 된다. 검색은 제출하지 않는다. |
| AnkiDroid 카드 목록 | Play용 변형에서 로컬 빈 컬렉션을 열고 왼쪽 탐색 메뉴를 연다. `Open Card browser`를 보내고 승인한다. | 원본 앱의 카드 브라우저에 **0 cards shown**이 보인다. 카드 생성·동기화 없이 판정할 수 있다. |
| AIMP 플레이어 메뉴 | 첫 실행의 **Welcome → Permissions**에서 오디오 접근을 허용하지 않고 Android 홈으로 간다. 앱을 강제 종료하지 않은 채 런처 아이콘으로 다시 열어 기본 플레이어를 표시한다. `/controls`, 이어서 `Open Main menu`를 보내고 승인한다. | 승인 후 메뉴에 **Queue**가 나타나고 재생목록 관리 아이콘의 접근성 이름이 **Managing playlists**인지 확인한다. 재생이나 오디오 파일 접근은 필요하지 않다. |

앱 버전과 언어가 바뀌면 컨트롤 이름과 결과 증거도 바뀔 수 있다. 네 앱의 이동에는 설치 앱의 패키지명, 컨트롤 ID 또는 역할과 주변·목적지 구조를 확인하는 코드가 있다. 새 앱에 같은 명령을 쓸 때는 독립적인 목적지 확인을 다시 수행한다.

## 2026-11-26 실행 결과

- 최종 디버그 APK(sha256 `95b005fbf9466c95ff46358956921e2a73798560bbf24b5e0d0dbf5ab50bf032`)로 위 네 앱의 이동과 VLC 승인 취소, Total Commander 파일명 입력을 모두 통과했다. 네 앱의 이동은 원래 앱에서 새 요소가 보이고 Hyperchat에서도 완료로 판정해야 통과했다.
- 시험한 구간에는 계정과 활성 기본 네트워크가 없었다. 대상 앱의 미디어·전체 파일 접근을 허용하지 않았다. Hyperchat에는 자체 고지와 별도로 Android 접근성 서비스를 설정했다. 앱에서 동의를 철회한 후 시스템의 활성·연결 서비스 목록이 비었고 채팅 버블이 사라졌다.
- 별도 합성 도서 목록에서 `달빛 검색하고 첫 번째 결과 열어줘`는 **달빛 정원**, `별빛 도서관 검색하고 일치하는 결과 열어줘`는 **별빛 도서관**의 상세 화면에 도달했다. 두 번째 실행에서 경로 재사용이 관찰됐다.
- 기존 Android 설정 시험에서도 **Display & touch**를 열어 원래 화면의 **Brightness level**을 확인했다. 이번에 최종 설치본으로 실행한 Android 계측 8건이 모두 통과했다. 접근성 동의의 거절·허용·철회는 이와 별도로 확인했다.
- JVM 시험 33개가 모두 통과했다. Android lint 오류 0개, 경고 13개다. 서명 전 AAB는 targetSdk 36이며 ARM64·x86_64의 네이티브 라이브러리 4개에서 16 KiB ELF 정렬을 확인했다. Galaxy/One UI, 실제 16 KiB 단말 실행, 공개 스토어 심사는 이번 실험의 범위 밖이다.

## 재현 방법

1. JDK 17과 Android SDK 36으로 `:app:assembleDebug :fixture:assembleDebug :fixture:assembleDebugAndroidTest`를 빌드한다. 전용 기기에 Hyperchat, fixture, fixture의 androidTest APK, 위 원본 앱들을 설치한다. AnkiDroid 두 변형의 패키지명은 같으므로 카드 앱의 이동 시험에는 Play용 변형을 설치한다. 이 변형은 GitHub 릴리스에서 직접 받을 수 있다.
2. Hyperchat의 **화면 접근 안내 보기**를 열어 동의하고 Android 접근성 설정에서 서비스를 켠다. 대상 앱은 일반 런처로 열고, 아래에 기록한 시작 화면을 준비한다. 사용자가 저장한 파일이나 계정은 필요하지 않다.
3. 대상 앱에서 Hyperchat 버블을 열어 지정한 문구를 입력한다. 읽기 요청은 즉시 답이 표시되며, 이동 요청은 항목 이름을 확인한 뒤 **동작 실행**을 누른다. 다른 화면으로 이동했는지는 대상 앱 패키지 소유의 화면 요소로 확인한다.
4. 자동 재현 시에는 버블이 닫힌 상태에서 시작한다. 각 실행 전에 대상 앱을 시작 화면으로 되돌린다. 실행기는 지정한 기기의 설치 상태와 기본 네트워크를 검사한다. `outcome=PASS`와 instrumentation 종료 코드 `-1`을 함께 확인한다.

```powershell
# ANDROID_HOME을 설정하고 platform-tools를 PATH에 추가한다.
# adb devices에서 확인한 전용 시험 기기 serial을 지정한다.
$serial = 'emulator-5554'
.\tools\inspect-legacy-apk.ps1 -Apk .\vlc.apk
.\tools\test-legacy-app.ps1 -Serial $serial -App vlc
.\tools\test-legacy-app.ps1 -Serial $serial -App vlc -Negative
.\tools\test-legacy-app.ps1 -Serial $serial -App tc
.\tools\test-legacy-app.ps1 -Serial $serial -App tc -FillName
.\tools\test-legacy-app.ps1 -Serial $serial -App aimp
.\tools\test-legacy-app.ps1 -Serial $serial -App anki
```

테스트 소스는 [HyperchatProbe.java](../fixture/src/androidTest/java/dev/hanzicho/hyperchat/fixture/HyperchatProbe.java)다. `/controls`에 표시된 답은 원래 앱에서 같은 이름의 컨트롤을 다시 찾아 대조한다. 이동 시험은 승인 전에는 목적지 요소가 없고, 승인 후에는 원래 앱에 그 요소가 나타나야 통과한다. 스크린샷은 fixture의 external files 디렉터리에 저장된다.
