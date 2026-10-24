# Kahon

Standing rules for anyone (human or agent) building this repository:

## Building

Always build the APK with:

```sh
./gradlew --no-daemon assembleRelease
```

The APK is produced at `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Publishing APKs

Always upload built APKs to https://up.sb (a bashupload instance) after a successful release build:

```sh
curl -s https://up.sb/kahon_<version>-<shortsha>-release.apk --data-binary @app/build/outputs/apk/release/app-release-unsigned.apk
```

Use the `https://` endpoint directly; the HTTP endpoint answers with a 301 that aborts the upload.
