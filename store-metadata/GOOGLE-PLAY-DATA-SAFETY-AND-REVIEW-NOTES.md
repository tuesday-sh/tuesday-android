# Google Play Console Data Safety & App Review Guide (`sh.tuesday.app` v1.0.0)

## 1. Upload Keystore & Signing Metadata (1Password `dev` Vault)
- **1Password Item**: `Tuesday.sh Android Upload Keystore (sh.tuesday.app)` (`id: ka22wdxqaqbqpyvr6nlh3dpnwa`)
- **Application ID**: `sh.tuesday.app`
- **Key Alias**: `tuesday-upload` (RSA-4096, PKCS12)
- **Upload Certificate SHA-256**: `49:81:DB:ED:C5:15:3F:96:A4:D8:18:F0:EE:97:EB:C1:17:51:44:4F:51:D7:F5:D5:D7:FB:E7:5B:BF:3B:BE:A4`

---

## 2. Google Play Data Safety Questionnaire Answers

### Data Collection & Security Overview
- **Does your app collect or share any of the required user data types?** `Yes`
- **Is all of the user data collected by your app encrypted in transit?** `Yes` (HTTPS TLS 1.3 via `https://api.tuesday.sh`)
- **Do you provide a way for users to request that their data is deleted?** `Yes` (In-app node/site reset & `https://tuesday.sh/privacy`)

### Specific Data Types

| Category | Data Type | Collected? | Shared? | Processed Ephemerally? | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Photos and videos** | Photos (Hardware label / chassis camera scan) | **No (Ephemeral Only)** | **No** | **Yes (`X-Tuesday-Image-Retention: none_ephemeral_ram_only`)** | App functionality (Hardware model, barcode, and MAC sticker OCR discovery). Camera frames are processed strictly in ephemeral RAM and immediately cleared; zero photos are written to disk, databases, or cloud buckets. |
| **Device or other IDs** | App-scoped installation UUID (`X-Tuesday-Device-ID`) | **Yes** | **No** | No | App functionality, fraud prevention, and API rate limiting (`30 req/hr` anonymous discovery). |
| **App info and performance** | Diagnostics / Latency telemetry | **Yes** | **No** | No | Analytics & reliability monitoring (`latencyMs`, token counts). |
| **Financial info** | Purchase history (`Google Play Billing v7` purchase token) | **Yes** | **No** | No | App functionality (Verifying `pro_tier_1` Multi-Site subscription entitlement via `/api/v1/billing/verify`). |

---

## 3. Google Play Billing v7 In-App Subscriptions (`pro_tier_1`)

- **Subscription Product IDs (Play Console -> Monetize -> Subscriptions)**:
  - `sh.tuesday.pro.tier1.monthly` — **\$4.99 / month** (Multi-Site & Cloud Sync)
  - `sh.tuesday.pro.tier1.annual` — **\$49.00 / year** (Save 18% — Multi-Site & Cloud Sync)
- **Server Verification**: `POST /api/v1/billing/verify` and `GET /api/v1/billing/status` (`entitlementTier = "pro_tier_1"`).

---

## 4. Google Play App Review Instructions (Copy/Paste into Play Console -> App Content -> App Access)

> **No login or physical network hardware is required to test all features of Tuesday.sh:**
> 1. Launch the app. On the initial **Welcome to Tuesday.sh** card (or inside the **Rack** tab), tap **"Explore 12-Device Demo Lab"**.
> 2. This immediately populates the **24-Port Managed Switch Grid** with 12 realistic homelab and smart-home devices across Trust Zones `Z0`–`Z4` (`UDM-Pro`, `USW-Pro-24`, `Synology DS923+`, `Home Assistant Yellow`, `LG OLED`, `Owlet Cam 2`, etc.).
> 3. Tap any lit port in the 24-Port Switch Grid to inspect its detail sheet, PURL software inventory, and security posture.
> 4. Tap the **Bulletins** tab at the bottom to view matched CISA KEV, CPSC Recall, and End-of-Life (EOL) advisories, toggle the **Confirmed vs. Guessed** filter, or test the **+ Add Node** camera/manual hardware discovery sheet.
