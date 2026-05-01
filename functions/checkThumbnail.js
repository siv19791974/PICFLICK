const functions = require('firebase-functions');
const admin = require('firebase-admin');

exports.checkThumbnail = functions
  .runWith({ memory: '256MB', timeoutSeconds: 60 })
  .https.onRequest(async (req, res) => {
    const bucket = admin.storage().bucket();
    const filePath = req.query.path || 'photos/gPj2AYpX7pUIYnZaCcHJPiMmh4A3/thumbnails_256/409b5290-38aa-4530-9f92-259be4940637.jpg';
    
    try {
      const [exists] = await bucket.file(filePath).exists();
      const [metadata] = exists ? await bucket.file(filePath).getMetadata() : [{}];
      
      res.json({
        bucketName: bucket.name,
        filePath,
        exists,
        metadata: exists ? {
          name: metadata.name,
          bucket: metadata.bucket,
          contentType: metadata.contentType,
          mediaLink: metadata.mediaLink,
          metadata: metadata.metadata
        } : null
      });
    } catch (err) {
      res.status(500).json({ error: err.message, code: err.code });
    }
  });
