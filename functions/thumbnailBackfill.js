const functions = require('firebase-functions');
const admin = require('firebase-admin');
const sharp = require('sharp');
const https = require('https');

/**
 * Download a file from a public HTTPS URL into a Buffer.
 */
function downloadUrl(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      if (res.statusCode !== 200) {
        reject(new Error(`HTTP ${res.statusCode} for ${url}`));
        return;
      }
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

/**
 * Core backfill logic shared by callable and HTTP trigger.
 */
async function runBackfill() {
  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  // Fetch all flicks and filter in-memory for those missing thumbnails.
  const allSnapshot = await db.collection('flicks')
    .select('imageUrl', 'thumbnailUrl256')
    .limit(500)
    .get();

  const docsToProcess = allSnapshot.docs.filter(doc => {
    const data = doc.data();
    return (!data.thumbnailUrl256 || data.thumbnailUrl256 === '') && data.imageUrl;
  });

  const snapshot = {
    empty: docsToProcess.length === 0,
    docs: docsToProcess
  };

  if (snapshot.empty) {
    return { processed: 0, message: 'No flicks need backfilling.' };
  }

  const results = [];
  const errors = [];

  for (const doc of snapshot.docs) {
    const flick = doc.data();
    const flickId = doc.id;
    const imageUrl = flick.imageUrl;

    if (!imageUrl || !imageUrl.includes('firebasestorage.googleapis.com')) {
      results.push({ flickId, status: 'skipped', reason: 'No valid imageUrl' });
      continue;
    }

    try {
      // Extract Storage path from download URL
      const urlObj = new URL(imageUrl);
      const encodedPath = urlObj.pathname.split('/o/')[1];
      if (!encodedPath) {
        results.push({ flickId, status: 'skipped', reason: 'Could not parse Storage path' });
        continue;
      }
      const filePath = decodeURIComponent(encodedPath);

      // Validate path is under photos/ or flicks/ (legacy)
      if (!filePath.startsWith('photos/') && !filePath.startsWith('flicks/')) {
        results.push({ flickId, status: 'skipped', reason: 'Not a photo path: ' + filePath });
        continue;
      }

      // Derive thumbnail paths
      const dir = filePath.substring(0, filePath.lastIndexOf('/'));
      const baseName = filePath.substring(filePath.lastIndexOf('/') + 1);
      const thumb256Path = `${dir}/thumbnails_256/${baseName}`;
      const thumb512Path = `${dir}/thumbnails_512/${baseName}`;

      // Download original via public URL (bypasses IAM permission issues)
      const originalBuffer = await downloadUrl(imageUrl);

      // Generate 256px thumbnail
      const thumb256Buffer = await sharp(originalBuffer)
        .resize(256, 256, { fit: 'inside', withoutEnlargement: false })
        .jpeg({ quality: 90, progressive: true })
        .toBuffer();

      // Generate 512px thumbnail
      const thumb512Buffer = await sharp(originalBuffer)
        .resize(512, 512, { fit: 'inside', withoutEnlargement: false })
        .jpeg({ quality: 90, progressive: true })
        .toBuffer();

      // Upload 256px
      const thumb256File = bucket.file(thumb256Path);
      await thumb256File.save(thumb256Buffer, {
        metadata: { contentType: 'image/jpeg', cacheControl: 'public, max-age=31536000' }
      });
      // Make public via ACL instead of signed URLs (avoids iam.serviceAccounts.signBlob)
      await thumb256File.acl.add({ entity: 'allUsers', role: 'READER' });
      const thumb256Url = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(thumb256Path)}?alt=media`;

      // Upload 512px
      const thumb512File = bucket.file(thumb512Path);
      await thumb512File.save(thumb512Buffer, {
        metadata: { contentType: 'image/jpeg', cacheControl: 'public, max-age=31536000' }
      });
      await thumb512File.acl.add({ entity: 'allUsers', role: 'READER' });
      const thumb512Url = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(thumb512Path)}?alt=media`;

      // Update Firestore flick document
      await doc.ref.update({
        thumbnailUrl256: thumb256Url,
        thumbnailUrl512: thumb512Url
      });

      results.push({ flickId, status: 'success', filePath });
      console.log(`Backfilled thumbnails for ${flickId}: ${filePath}`);

    } catch (err) {
      console.error(`Failed to backfill ${flickId}:`, err.message);
      errors.push({ flickId, error: err.message });
    }
  }

  return {
    processed: results.length,
    successes: results.filter(r => r.status === 'success').length,
    skipped: results.filter(r => r.status === 'skipped').length,
    errors: errors.length,
    details: results,
    errorDetails: errors
  };
}

/**
 * Admin-only HTTPS callable: backfill 256px and 512px thumbnails for all flicks
 * that are missing thumbnail URLs. Safe to re-run (idempotent).
 */
exports.backfillThumbnails = functions
  .runWith({ memory: '1GB', timeoutSeconds: 540 })
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'Authentication required.');
    }
    const developerUid = 'LpSqE40IZGeAGMknTAEzysqp5l33';
    if (context.auth.uid !== developerUid) {
      throw new functions.https.HttpsError('permission-denied', 'Admin only.');
    }
    return runBackfill();
  });

/**
 * HTTP trigger for easy curl invocation. Pass ?token=picflick2026 as query param.
 */
exports.backfillThumbnailsHttp = functions
  .runWith({ memory: '1GB', timeoutSeconds: 540 })
  .https.onRequest(async (req, res) => {
    const secretToken = 'picflick2026';
    if (req.query.token !== secretToken) {
      res.status(403).json({ error: 'Invalid or missing token. Use ?token=picflick2026' });
      return;
    }
    try {
      const result = await runBackfill();
      res.status(200).json(result);
    } catch (err) {
      console.error('Backfill HTTP error:', err);
      res.status(500).json({ error: err.message });
    }
  });
