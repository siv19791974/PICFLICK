const functions = require('firebase-functions');
const admin = require('firebase-admin');

/**
 * One-off cleanup: delete all legacy thumbnails_256/ files from Storage
 * and remove thumbnailUrl256 from every flick document.
 */
async function runCleanup256() {
  const db = admin.firestore();
  const bucket = admin.storage().bucket();

  const allSnapshot = await db.collection('flicks')
    .select('imageUrl', 'thumbnailUrl256')
    .limit(500)
    .get();

  const results = [];
  const errors = [];

  for (const doc of allSnapshot.docs) {
    const flick = doc.data();
    const flickId = doc.id;

    if (!flick.imageUrl) {
      results.push({ flickId, status: 'skipped', reason: 'No imageUrl' });
      continue;
    }

    try {
      const urlObj = new URL(flick.imageUrl);
      const encodedPath = urlObj.pathname.split('/o/')[1];
      if (!encodedPath) {
        results.push({ flickId, status: 'skipped', reason: 'Bad imageUrl' });
        continue;
      }
      const filePath = decodeURIComponent(encodedPath);
      const dir = filePath.substring(0, filePath.lastIndexOf('/'));
      const baseName = filePath.substring(filePath.lastIndexOf('/') + 1);
      const thumb256Path = `${dir}/thumbnails_256/${baseName}`;

      // Delete Storage object if it exists
      let deleted = false;
      try {
        const thumb256File = bucket.file(thumb256Path);
        const [exists256] = await thumb256File.exists();
        if (exists256) {
          await thumb256File.delete();
          deleted = true;
        }
      } catch (delErr) {
        // ignore
      }

      // Remove thumbnailUrl256 from Firestore document
      const updates = {};
      if (flick.thumbnailUrl256 !== undefined) {
        updates.thumbnailUrl256 = admin.firestore.FieldValue.delete();
      }
      if (Object.keys(updates).length > 0) {
        await doc.ref.update(updates);
      }

      results.push({ flickId, status: 'cleaned', deletedFromStorage: deleted });
    } catch (err) {
      errors.push({ flickId, error: err.message });
    }
  }

  return {
    processed: results.length,
    cleaned: results.filter(r => r.status === 'cleaned').length,
    skipped: results.filter(r => r.status === 'skipped').length,
    errors: errors.length,
    details: results,
    errorDetails: errors
  };
}

exports.cleanup256Http = functions
  .runWith({ memory: '512MB', timeoutSeconds: 300 })
  .https.onRequest(async (req, res) => {
    const secretToken = 'picflick2026';
    if (req.query.token !== secretToken) {
      res.status(403).json({ error: 'Invalid or missing token. Use ?token=picflick2026' });
      return;
    }
    try {
      const result = await runCleanup256();
      res.status(200).json(result);
    } catch (err) {
      console.error('Cleanup256 error:', err);
      res.status(500).json({ error: err.message });
    }
  });
