# Google Play Service Account Setup Guide
## For PicFlick Receipt Validation

---

## 🎯 WHAT YOU'RE SETTING UP

A **service account** that allows your Firebase Cloud Functions to securely verify purchases with Google Play's servers. This prevents users from faking purchases.

---

## 📋 STEP-BY-STEP SETUP

### **Step 1: Create Google Cloud Project (if not exists)**

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Select your Firebase project (should be `picflick-4793175b`)
3. Or create a new project if needed

---

### **Step 2: Enable Google Play Developer API**

1. In Google Cloud Console, go to **"APIs & Services"** → **"Library"**
2. Search for **"Google Play Developer API"**
3. Click **"Enable"**

---

### **Step 3: Create Service Account**

1. Go to **"APIs & Services"** → **"Credentials"**
2. Click **"+ CREATE CREDENTIALS"** → **"Service account"**
3. Fill in details:
   ```
   Service account name: picflick-billing-validator
   Service account ID: picflick-billing-validator
   Description: Validates PicFlick in-app purchases
   ```
4. Click **"CREATE AND CONTINUE"**
5. Grant roles: **"Editor"** (or minimum: "Service Account User")
6. Click **"CONTINUE"** → **"DONE"**

---

### **Step 4: Create JSON Key**

1. Find your new service account in the list
2. Click on it to open details
3. Go to **"Keys"** tab
4. Click **"ADD KEY"** → **"Create new key"**
5. Select **"JSON"** format
6. Click **"CREATE"**
7. **⚠️ IMPORTANT:** A JSON file will download. Keep it SECURE!

---

### **Step 5: Grant Google Play Access**

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your PicFlick app
3. Go to **"Users and permissions"**
4. Click **"Invite new users"**
5. Enter the service account email:
   ```
   picflick-billing-validator@picflick-4793175b.iam.gserviceaccount.com
   ```
   (Use the actual email from your service account)
6. Set role: **"Finance"** (minimum needed for purchase validation)
7. Click **"Send invite"**

---

### **Step 6: Add Service Account to Firebase**

You need to add the service account JSON to Firebase Functions config:

#### **Option A: Using Firebase CLI (Recommended)**

1. Install Firebase CLI if not already:
   ```bash
   npm install -g firebase-tools
   ```

2. Login to Firebase:
   ```bash
   firebase login
   ```

3. Set the service account config:
   ```bash
   firebase functions:config:set googleplay.service_account="$(cat path/to/downloaded-key.json)"
   ```

   Or paste the JSON content directly:
   ```bash
   firebase functions:config:set googleplay.service_account='{"type":"service_account",...}'
   ```

#### **Option B: Manual Environment Variable**

1. Base64 encode your JSON key:
   ```bash
   base64 -i path/to/downloaded-key.json -o encoded.txt
   ```

2. In Firebase Console → Project Settings → Service accounts → Generate new private key

---

### **Step 7: Deploy Updated Functions**

1. Navigate to functions directory:
   ```bash
   cd C:\Users\Gaelle\AndroidStudioProjects\PICFLICK3\functions
   ```

2. Install new dependencies:
   ```bash
   npm install
   ```

3. Deploy the validation function:
   ```bash
   firebase deploy --only functions
   ```

---

### **Step 8: Deploy Firestore Security Rules**

1. In Firebase Console, go to **"Firestore Database"** → **"Rules"**
2. Copy the contents of `firestore.rules` from your project
3. Paste into the rules editor
4. Click **"Publish"**

---

### **Step 9: Test Validation**

1. Build and install your app on a test device
2. Make a test purchase (won't charge real money in sandbox)
3. Check Firebase Functions logs:
   - Go to Firebase Console → Functions → Logs
   - Look for "Purchase validated" messages
4. Check Firestore - user document should have:
   - `subscriptionTier` updated
   - `subscriptionExpiryDate` set
   - `purchaseToken` saved

---

## 🔐 SECURITY BEST PRACTICES

### **✅ DO:**
- Keep the JSON key file SECRET (never commit to Git!)
- Store it in Firebase config (encrypted)
- Use environment variables for local testing
- Restrict service account permissions to minimum needed
- Rotate keys periodically (every 90 days)

### **❌ DON'T:**
- Never commit the JSON key to version control
- Never share the key in emails or messages
- Never expose the key in client-side code
- Don't grant Owner permissions to the service account

---

## 🆘 TROUBLESHOOTING

### **"Error: Service account not configured"**
- Make sure you set the Firebase config correctly in Step 6
- Verify the service account email is correct

### **"Permission denied" when validating**
- Check Step 5 - service account needs "Finance" role in Play Console
- API might not be enabled (Step 2)

### **"Purchase not found"**
- Purchase might not be acknowledged yet
- Product ID might not match exactly (case-sensitive!)
- Purchase might be expired

### **Functions fail to deploy**
- Make sure `googleapis` package is installed: `npm install googleapis`
- Check Node.js version matches (should be 18 or 20)

---

## 📁 FILES CREATED

| File | Purpose |
|------|---------|
| `functions/purchaseValidation.js` | Server-side validation logic |
| `firestore.rules` | Security rules protecting subscription data |
| `functions/index.js` | Updated to export validation functions |
| `functions/package.json` | Added googleapis dependency |

---

## 🔄 WHAT HAPPENS NOW?

When a user makes a purchase:

1. **Client** (BillingViewModel) → Google Play purchase flow
2. **Google Play** → Returns purchase token
3. **Client** → Acknowledges purchase
4. **Client** → Calls `validatePurchase` Cloud Function
5. **Cloud Function** → Verifies with Google Play API using service account
6. **Cloud Function** → Updates Firestore with verified subscription
7. **Firestore** → Triggers `onSnapshot` listeners (UI updates)
8. **User** → Gets access to premium features!

---

## 📊 MONITORING

Track validation health in Firebase Console:

**Functions → Logs:**
- Look for "Purchase validated" messages
- Watch for errors
- Monitor execution times

**Firestore:**
- Check user documents have correct subscription data
- Verify expiry dates are accurate

---

## 🎯 NEXT STEPS

After setting up service account:

1. ✅ Test sandbox purchases
2. ✅ Verify receipts validate correctly
3. ✅ Check subscription expiry handling
4. ✅ Test downgrade on expiry
5. ✅ Deploy to production track
6. ✅ Monitor real purchases

---

## 📞 SUPPORT

If you get stuck:
1. Check Firebase Functions logs first
2. Verify all 9 steps above
3. Test with development mode (bypass enabled)
4. Enable detailed logging

---

**Your PicFlick app now has enterprise-grade purchase security!** 🛡️💰
