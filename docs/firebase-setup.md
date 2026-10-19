# Firebase setup

Accounts and cloud backup (FR-8) run on a Firebase project of your own. The app builds and runs
without one — the Account page then says cloud backup isn't set up — so this is only needed to
switch the feature on. Everything stays on the free (Spark) plan: Authentication and Cloud
Firestore are free at this scale, and nothing here uses Cloud Storage or Cloud Functions.

The one file this produces, `app/google-services.json`, holds the project's keys and is
git-ignored. Each developer (and each build machine) puts their own copy in place.

## 1. Create the project

1. Open <https://console.firebase.google.com> and click **Add project**.
2. Name it (for example `liftbook`). Google Analytics isn't needed; switch it off.
3. Wait for the project to be created, then open it.

## 2. Register the Android app — twice

The debug build installs beside the release build with its own application id, so the project
needs both ids, or the debug build fails with "No matching client found for package name".

1. On the project overview, click the **Android** icon (**Add app**).
2. **Android package name:** `com.example.liftbook`. Give it a nickname such as "LiftBook".
3. **Debug signing certificate SHA-1:** needed for Google sign-in. Get it from the repo root:

   ```bash
   ./gradlew signingReport
   ```

   Copy the `SHA1:` line under `Variant: debug` (the `~/.android/debug.keystore` certificate).
   If you sign release builds with your own keystore (`keystore.properties`), add that
   variant's SHA-1 too, later, under **Project settings → Your apps → Add fingerprint**.
4. Click **Register app**, then **Download google-services.json**. Don't add anything else the
   wizard suggests; the Gradle plugin is already wired in.
5. Back on the overview, **Add app** again with package name `com.example.liftbook.debug` and
   the same SHA-1. Download `google-services.json` again — this second download contains
   **both** apps. Put it at `app/google-services.json`.

## 3. Turn on sign-in methods

1. In the left menu open **Build → Authentication**, click **Get started**.
2. On the **Sign-in method** tab, add **Email/Password**: enable it, leave *Email link
   (passwordless sign-in)* off, save.
3. Add **Google**: enable it, pick a support email, save. This creates the web client id that
   the app reads at runtime (`default_web_client_id`, generated into resources by the plugin).
4. Because enabling Google changes the configuration, download `google-services.json` once
   more from **Project settings → Your apps** and replace the file in `app/`. The file is only
   complete once it contains an `oauth_client` entry with `"client_type": 3`.

No email verification is required: the app signs an account in as soon as it's created.
Password reset emails use Firebase's default template; you can change the sender name and the
text under **Authentication → Templates**.

## 4. Create the database and set its rules

1. **Build → Firestore Database → Create database.** Choose a location near you and start in
   **production mode** (locked down).
2. Open the **Rules** tab, replace the contents with `firebase/firestore.rules` from this repo,
   and **Publish**. The rule allows each signed-in account to read and write only
   `users/{its own uid}` and what's below it.

   ```
   match /users/{userId}/{document=**} {
     allow read, write: if request.auth != null && request.auth.uid == userId;
   }
   ```

No indexes or collections need creating by hand; the app writes `users/{uid}` and
`users/{uid}/chunks/…` itself.

## 5. Build and check

```bash
./gradlew installDebug
```

The build log no longer prints "google-services.json not found". In the app: **Settings →
Account → Create account**, then **Back up now**. In the console, **Firestore Database →
Data** shows `users/<uid>` with the backup's counts and a `chunks` subcollection.

Google sign-in needs Google Play services on the device, and a Google account signed in on it.
On an emulator, use a **Google Play** system image, not a plain AOSP one.

## What's stored where

- **Authentication** holds accounts: email, password hash, or the linked Google account.
- **Firestore** holds one backup per account under `users/{uid}`: the document names when it
  was saved, what it holds (counts) and which pieces make it; the pieces in `chunks/` are the
  same JSON a file export writes (FR-6.3), gzip-compressed and cut under Firestore's 1 MiB
  document limit. Nothing is shared between accounts.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Build fails: *No matching client found for package name 'com.example.liftbook.debug'* | Step 2.5 skipped: register the `.debug` app and re-download the file. |
| "Google sign-in isn't set up in this build yet" | The config file has no `oauth_client` entry: the Google provider isn't enabled, or no SHA-1 is registered (steps 2.3 and 3). Fix that, then download the file again. |
| "Google sign-in isn't available on this phone" | No Google account or no Play services on the device; or the SHA-1 isn't registered (step 2.3); or the file predates enabling Google (step 3.4). |
| "The server refused the request. Check the project's Firestore rules." | Step 4.2: rules not published, or the database was never created. |
| "No connection" on every attempt | The emulator or phone has no internet, or the Firestore location is still being provisioned (wait a minute). |
| Sign-in works but backups never appear | The Account page says *Finish setting up*: answer the question it asks about which data to keep. |
