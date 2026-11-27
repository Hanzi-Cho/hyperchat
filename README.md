# Hyperchat

Android 화면 위의 작은 채팅 버블로 지금 열린 앱의 버튼을 읽고, 원하는 항목을 열거나 입력합니다.

**[v0.0.1 APK 다운로드](https://github.com/Hanzi-Cho/hyperchat/releases/download/v0.0.1/Hyperchat-0.0.1.apk)** · **[릴리스 및 SHA-256](https://github.com/Hanzi-Cho/hyperchat/releases/tag/v0.0.1)**

파일 관리자의 검색 버튼처럼, 쓰고 싶은 기능은 알아도 메뉴 이름이나 위치를 모를 때가 있습니다.

대상 앱을 열고 `버튼 목록`을 보낸 뒤, 원하는 항목을 지정하세요. Hyperchat이 실행할 동작을 보여주면 직접 승인합니다.

```powershell
curl.exe -fL -o Hyperchat-0.0.1.apk https://github.com/Hanzi-Cho/hyperchat/releases/download/v0.0.1/Hyperchat-0.0.1.apk
adb install Hyperchat-0.0.1.apk
adb shell am start -n dev.hanzicho.hyperchat/.ui.SettingsActivity
```

위 명령은 Windows에 Android Platform-Tools가 있고 USB 디버깅을 허용한 Android 기기 한 대가 연결된 경우에 사용합니다. **휴대폰에서 APK 링크를 눌러 직접 설치하거나, PC로 받은 APK를 휴대폰에 복사해 열어도 됩니다.** Android 8.0 이상, 64비트 ARM 또는 x86-64가 필요합니다. 첫 체험은 계정, API 키, 모델 다운로드 없이 진행합니다. [Platform-Tools](https://developer.android.com/tools/releases/platform-tools)

## 첫 실행 · Android 설정에서 디스플레이 열기

1. **Hyperchat → 화면 접근 안내 보기**에서 읽는 정보와 사용 목적을 확인하고 **동의하고 Android 설정 열기**를 누릅니다. Android 접근성 설정에서 **Hyperchat**을 선택합니다. 이 항목이 회색이면 아래의 **APK 직접 설치 시 접근성 켜기**를 먼저 따르세요. 기기에 표시된 서비스 스위치를 켜고 Android의 권한 대화상자를 확인합니다.
2. **Android 설정의 첫 화면**으로 돌아갑니다. 아래 명령은 Android 16 영어 화면 기준입니다. **Display & touch** 행이 보이도록 필요하면 직접 스크롤합니다.
3. 떠 있는 Hyperchat 버블을 누르고 `버튼 목록` 또는 `/controls`를 보냅니다. 현재 화면에서 읽을 수 있는 버튼 이름을 최대 8개 보여줍니다.
4. `Open Display & touch`를 보내고 제안된 항목 이름을 확인한 뒤 **동작 실행**을 누릅니다. 설정의 **Display & touch** 화면에서 **Brightness level**을 찾으면 성공입니다. 버블이 내용을 가리면 뒤로 가기를 한 번 눌러 접습니다.

화면 이름은 Android 언어와 제조사에 따라 달라집니다. 다른 화면에서 시도할 때는 현재 보이는 항목 이름을 사용하세요. 접근은 **Hyperchat → 화면 접근 동의 철회**에서 끌 수 있습니다.

### APK 직접 설치 시 접근성 켜기

Android 16 영어 에뮬레이터에서 파일 앱으로 설치한 뒤 확인한 순서입니다. 접근성은 다른 앱의 화면을 읽고 조작할 수 있는 권한입니다. 배포처와 앱의 고지를 확인한 후 본인이 허용할 때만 진행하세요. [Android 제한된 설정 안내](https://support.google.com/android/answer/12623953?hl=ko)

1. 접근성에서 회색 **Hyperchat · Controlled by Restricted Setting**을 누르면 **App was denied access**가 나타납니다. **Close**를 누릅니다.
2. Android **설정 → 앱 → Hyperchat**의 **App info**를 엽니다. 오른쪽 위 **⋮ → Allow restricted settings**를 선택합니다.
3. **설정 → 접근성 → Hyperchat**으로 돌아갑니다. 시험 기기에서는 **Hyperchat shortcut** 오른쪽 스위치를 켜자 권한 창이 열렸습니다. **Allow → Got it**을 누르면 버블이 나타납니다. 이제 위의 첫 실행 2번부터 진행하세요.

## 누구에게 유용한가

| 사용자 | 먼저 해볼 일 |
| --- | --- |
| 자주 쓰는 앱의 메뉴 위치를 매번 다시 찾는 사람 | Total Commander 홈에서 파일 찾기 창을 열고 파일명을 입력합니다. |
| 화면 내용을 기기 안에서 다루고 싶은 사람 | 모델 없이 컨트롤을 읽습니다. 로컬 모델을 추가하면 현재 화면이나 직접 선택한 문서에 관해 질문할 수 있습니다. |
| 기존 앱에 AI 사용 흐름을 실험하는 Android 개발자·QA | 수정하지 않은 앱에서 승인 전후 화면을 비교하고, 같은 절차를 저장소의 계측 시험으로 재현합니다. |

## 함께 쓰기 좋은 앱

영문 UI에서 재현한 예입니다. **열기와 입력은 각각 승인**합니다. 먼저 대상 앱에서 표의 시작 화면을 열어 주세요.

| 앱과 검증 버전 | 시작 화면 → 보낼 문장 | 원래 앱에서 보이는 결과 |
| --- | --- | --- |
| **VLC for Android 3.7.2** · 가장 쉬운 첫 체험 | **Video** 탭 → `Open Audio` | **Audio** 탭에 **ARTISTS**가 나타납니다. 빈 라이브러리에서도 확인할 수 있습니다. [VLC 설치](https://play.google.com/store/apps/details?id=org.videolan.vlc) |
| **Total Commander 3.62d** · 두 번의 승인 체험 | 홈 → `Open Search...` → 열린 창의 파일명 칸을 탭 → `Type hyperchat-demo.txt` | 첫 승인은 **Find files**를 엽니다. 두 번째 승인은 파일명 칸을 채우며 검색은 사용자가 제출합니다. [공식 배포처](https://www.ghisler.com/fdroid.htm) |
| **AnkiDroid 2.25.3** · 빈 학습 덱으로 체험 | Play용 변형에서 왼쪽 탐색 메뉴 열기 → `Open Card browser` | 빈 컬렉션의 카드 브라우저에 **0 cards shown**이 보입니다. [AnkiDroid 설치](https://play.google.com/store/apps/details?id=com.ichi2.anki) |
| **AIMP 4.31.1751** · 음악 플레이어 메뉴 탐색 | 기본 플레이어 → `Open Main menu` | 메뉴에 **Queue**가 나타납니다. 첫 실행의 권한 안내에서 플레이어로 재진입하는 절차는 시험 문서에 있습니다. [공식 배포처](https://aimp.ru/?do=download&os=android) |

각 앱의 배포 변형, 고정 버전, 시작 화면, 원본 앱에서의 판정 조건을 기록했습니다. 앱 업데이트나 UI 번역에 따라 컨트롤 이름이 달라질 수 있습니다. [앱별 재현 절차](docs/legacy-app-scenarios.md)

## 현재 기능과 데이터 처리

| 기능 | 동작 |
| --- | --- |
| 화면 읽기 | 현재 앱이 Android 접근성에 제공하는 이름 있는 버튼을 읽습니다. 암호·민감 노드는 걸러내며, 버튼 목록에 입력란 값을 싣지 않습니다. |
| 앱 안에서 동작 | 지원 화면의 검색, 결과 열기, 현재 포커스된 칸에 입력, 뒤로 가기와 스크롤을 요청할 수 있습니다. 실행 전 대상을 확인하며, 승인 전 초안을 고치면 이전 제안이 취소됩니다. |
| 화면 질문과 첨부 | **+ → 로컬 모델**에서 호환 `.litertlm`을 가져옵니다. 선택한 사진은 기기에서 글자를 인식하고, UTF-8 파일은 텍스트를 읽어 모델에 전달합니다. 모델 답변은 부정확할 수 있습니다. [검증한 모델과 소요 시간](docs/testing.md#earlier-natural-language-model-review) |
| 버블과 바로가기 | 탭·위로 스와이프·시스템 바로가기 동작을 개별 설정합니다. 길게 누르고 끌면 버블이 이동합니다. [버블 복구 및 하드웨어 키 설정](docs/shortcuts.md) |

Hyperchat APK에는 인터넷 권한이 없습니다. 승인한 검색어나 입력값은 대상 앱에 전달되며, 그 앱은 자체 정책에 따라 네트워크를 사용할 수 있습니다. 검증된 검색 경로의 컨트롤 ID와 앱 버전은 기기에 저장합니다. 동작 범위는 현재 앱이 접근성으로 노출하는 화면입니다. 다른 앱 실행과 픽셀 좌표 기반 조작은 지원 범위 밖입니다. [처리 흐름 및 경계](docs/architecture.md)

## 개발자와 AI 코딩 도구를 위한 재현

JDK 17, Android SDK Platform 36, Build Tools 35.0.0을 준비합니다. 처음 빌드할 때는 Gradle과 의존성을 다운로드할 네트워크가 필요합니다. Windows PowerShell에서는 아래의 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.

```sh
git clone --branch v0.0.1 --depth 1 https://github.com/Hanzi-Cho/hyperchat.git
cd hyperchat
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

디버그 빌드 결과는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 같은 패키지명의 GitHub 릴리스와 로컬 디버그 빌드는 서명 키가 다릅니다. 두 빌드를 번갈아 시험할 때는 별도 테스트 기기 또는 에뮬레이터를 쓰면 기존 데이터를 보존할 수 있습니다.

| 찾는 항목 | 진입점 |
| --- | --- |
| 패키지, Android 버전, ABI, 모델 런타임 | [app/build.gradle.kts](app/build.gradle.kts) · [AndroidManifest.xml](app/src/main/AndroidManifest.xml) |
| 앱 시작, 동의, 단축키 설정 | [SettingsActivity.kt](app/src/main/java/dev/hanzicho/hyperchat/ui/SettingsActivity.kt) |
| 지원 명령과 실제 화면 실행 | [ScreenCommand.kt](app/src/main/java/dev/hanzicho/hyperchat/core/ScreenCommand.kt) · [HyperchatService.kt](app/src/main/java/dev/hanzicho/hyperchat/platform/HyperchatService.kt) |
| 선택 모델 가져오기 | Windows에서 [get-model.ps1](tools/get-model.ps1)을 실행하면 고정한 Qwen3-0.6B 파일의 SHA-256을 검사합니다. 파일을 기기에 옮긴 후 앱의 **+ → 로컬 모델**에서 선택합니다. |
| 기기 시험과 성공 조건 | [HyperchatProbe.java](fixture/src/androidTest/java/dev/hanzicho/hyperchat/fixture/HyperchatProbe.java) · [시험 명령 및 이력](docs/testing.md) · [UI 설계](docs/design-system.md) |

## 검증 범위

- **v0.0.1 배포본**의 설치·실행 결과와 SHA-256은 [GitHub Release](https://github.com/Hanzi-Cho/hyperchat/releases/tag/v0.0.1)에서 확인할 수 있습니다.
- **모델 없는 실앱 시험**은 Android 16 영어 에뮬레이터에서 수행했습니다. 테스트는 대상 앱 소유의 컨트롤을 검사합니다. **합성 도서 목록 시험**은 두 검색어가 각각 올바른 상세 화면을 여는지 별도로 확인합니다. [재현 절차와 관찰 결과](docs/legacy-app-scenarios.md)
- **모델 시험**에서는 Qwen3-0.6B가 에뮬레이터에서 Android 설정의 실제 화면 이름을, TC501 실기기에서 시험 앱의 검색 버튼 이름을 답했습니다. v0.0.1의 Galaxy/One UI 실기기 조작은 미검증입니다. [모델 시험 기록](docs/testing.md) · [변경 이력](docs/CHANGELOG.md)
