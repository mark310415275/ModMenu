СПОСОБ 1 (без установки программ, через GitHub):
1. Зайди на github.com, создай бесплатный аккаунт и новый репозиторий.
2. Нажми "uploading an existing file" и перетащи ВСЕ файлы из этой папки (включая папку .github).
3. Открой вкладку Actions -> Build APK -> дождись зелёной галочки (2-5 минут).
4. Внутри запуска внизу в "Artifacts" скачай ModMenu-APK -> внутри лежит app-debug.apk.

СПОСОБ 2 (Android Studio):
File -> Open -> выбери эту папку -> дождись синхронизации -> Build -> Build APK(s).

Свой дизайн меню: файл app/src/main/assets/mod_menu.html
Свой код для кнопок: app/src/main/java/com/example/modmenu/OverlayService.kt (места с TODO)
