# Publishing FloatNote on Google Play

Everything that can be automated is done: the signed App Bundle, store text, icon,
feature graphic, privacy policy, a banner ad, in-app supporter tips, and an upload step in CI.
The steps below need **you**, signed in to Google as the account owner, because Google
requires the owner to verify their identity and accept its agreements.

Use the Google account you want to publish under.

## 1. Create the developer account (one time, ~$25)

1. Go to <https://play.google.com/console/signup> while signed in with that account.
2. Choose **Personal** (or **Organization** if you have a registered business / D-U-N-S number).
3. Pay the one-time $25 fee and complete **identity verification** (government ID).
   Verification can take a few days.
4. Set up a **payments profile** (Play Console → Settings → Payments profile) so you can
   receive supporter tip revenue. Add your bank account there.

> New *personal* accounts must run a **closed test with at least 12 testers for 14 days**
> before they can publish to Production. Organization accounts skip this.

## 2. Turn on release signing in GitHub (5 minutes)

Claude generated an upload key for you. Add the four values from `github-secrets.txt`
as repository secrets at
<https://github.com/sachin1024-92/vitacreate-ai/settings/secrets/actions>:

| Secret | Value |
|---|---|
| `FLOATNOTE_KEYSTORE_BASE64` | contents of `FLOATNOTE_KEYSTORE_BASE64.txt` |
| `FLOATNOTE_KEYSTORE_PASSWORD` | from `github-secrets.txt` |
| `FLOATNOTE_KEY_ALIAS` | `floatnote` |
| `FLOATNOTE_KEY_PASSWORD` | from `github-secrets.txt` |

Then re-run the **FloatNote Android** workflow. Its `FloatNote.aab` is now Play-ready.
Store `floatnote-upload.jks` and the password in a password manager.

## 3. Create the app in Play Console

1. **Create app** → name `FloatNote: Floating Notepad`, default language English,
   **App**, **Free**. Accept the declarations.
2. **Store listing** (Grow → Store presence → Main store listing). Copy from
   `fastlane/metadata/android/en-US/`:
   - Short description: `short_description.txt`
   - Full description: `full_description.txt`
   - App icon: `images/icon.png` (512×512)
   - Feature graphic: `images/featureGraphic.png` (1024×500)
   - Phone screenshots: take 2–8 on your phone (the notes list, the bubble over another
     app, the open notepad, the Support screen with colours).
3. **App content** (Policy → App content):
   - **Privacy policy URL**: publish `PRIVACY.md` (e.g. enable GitHub Pages, or use the
     file's GitHub URL) and paste the link.
   - **Ads**: **Yes, contains ads** (one banner on the notes list).
   - **App access**: All functionality is available without special access.
   - **Content rating**: complete the questionnaire (Utility/Productivity, no
     objectionable content) → Everyone.
   - **Target audience**: 13+ (avoid "children" to keep the Families policy out of scope).
   - **Data safety**: the app contains the Google Mobile Ads SDK. Declare what Google lists
     for that SDK (<https://developers.google.com/admob/android/privacy/play-data-disclosure>):
     approximate location, app interactions, diagnostics and device or other IDs, collected
     and shared for advertising and analytics. Notes are **not** collected. Payments are
     processed by Google Play; FloatNote never receives payment details.
   - **Foreground service permissions**: declare **Special use**, describe it as
     "Keeps the user-enabled floating notepad bubble visible over other apps", and
     attach a short screen recording of turning the bubble on and using it.
   - **Government apps / Financial features / Health**: No.

## 4. Upload the first release

1. Download `FloatNote.aab` from the latest workflow run (signed with your key from step 2).
2. Testing → **Closed testing** → create a track → **Create new release**.
3. Accept **Play App Signing** (Google holds the app signing key; yours is the upload key).
4. Upload the `.aab`, add release notes from `whatsnew/whatsnew-en-US`, and roll out.
5. Add 12+ testers (a Google Group or email list) and keep the test running for 14 days.
   Then apply for **Production** access from the Dashboard.

## 5. Ads (revenue)

Set up on 14 Nov 2026 in the AdMob account that belongs to the Play developer account:

- App: **FloatNote** (Android), app ID `ca-app-pub-7040144214936380~9265551190`
- Ad unit: **Notes list banner**, ID `ca-app-pub-7040144214936380/2339236109`

Both are built into **release** builds. **Debug** builds always use Google's test IDs, so
install `FloatNote-debug.apk` for your own testing and never tap real ads in the release
build: AdMob bans accounts for that.

Still to do:

1. AdMob is re-verifying the account after reactivation (usually 24 hours, up to two weeks).
   Ads do not serve until that finishes.
2. In AdMob → **Privacy & messaging**, create the **European regulations** consent message.
   The app already asks for consent before loading ads; it needs this message to show.
3. Add payment details in AdMob (payouts start once you pass the USD 100 threshold).
4. After the app is live on Play, open the app in AdMob → **App settings** and link it to
   its store listing. Ad serving stays limited until Google has reviewed the listed app.

Be realistic: one banner earns a fraction of a rupee per view. Income only becomes
meaningful with thousands of daily users, so the supporter tips below matter more early on.

## 6. Create the supporter tips (revenue)

Monetize → Products → **In-app products** → create three **one-time** products with
exactly these IDs (the app looks them up by ID):

| Product ID | Suggested name | Suggested price |
|---|---|---|
| `supporter_coffee` | Buy me a coffee ☕ | $1.99 / ₹89 |
| `supporter_lunch` | Buy me lunch 🍱 | $4.99 / ₹249 |
| `supporter_hero` | FloatNote hero 🦸 | $9.99 / ₹499 |

Activate each. Any tip removes the ad and unlocks the extra bubble colours, and it restores automatically on
a reinstall or new phone. Google keeps 15% (on your first $1M/year).

To test purchases without being charged: Settings → **License testing** → add your
tester emails, then buy from a build installed via the testing track.

## 7. Later releases: fully automatic

1. In Google Cloud, create a service account, enable the **Google Play Android Developer
   API**, and create a JSON key.
2. In Play Console → Users and permissions → invite the service account email with
   *Release apps to testing tracks* permission.
3. Save the JSON as the GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`.
4. Bump `versionCode` in `app/build.gradle.kts`, then
   `git tag floatnote-v1.1.0 && git push origin floatnote-v1.1.0`.
   CI builds, creates a GitHub Release, and uploads the bundle to the internal track as
   a draft for you to promote.
