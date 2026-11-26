# 스터디윗미 (Study With Me)

개인용 공부 + 집중 잠금 안드로이드 앱입니다. 서버·로그인·분석이 없고 모든 데이터는 기기 안에만 저장됩니다.

- 스택: Kotlin, Jetpack Compose, Material 3, Room, DataStore, Haze(배경 블러), Pretendard 폰트(OFL)
- 최소 SDK 31 / 타깃 SDK 37
- 디버그 APK 빌드: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `app/debug.keystore`는 모든 빌드를 같은 서명으로 만들기 위한 **디버그 전용** 키입니다(그래야 새 APK를 데이터 삭제 없이 덮어 설치할 수 있어요). 앱을 스토어에 올릴 일이 없는 개인용이라 저장소에 포함했습니다.
- `main` 외 모든 브랜치에 푸시하면 GitHub Actions가 APK를 자동으로 빌드해 `StudyWithMe-debug-apk` 아티팩트로 올려줍니다.
