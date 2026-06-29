package mihon.app.shizuku;

import android.content.IntentSender;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;

    void uninstall(String packageName, in IntentSender statusSender) = 2;

    void destroy() = 16777114;
}
