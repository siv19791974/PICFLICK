const admin = require('firebase-admin');

// Initialize using application default credentials (should work in Cloud Functions env)
// But we're running locally, so we need to use the service account from the project
admin.initializeApp();

const bucket = admin.storage().bucket();
const filePath = 'photos/gPj2AYpX7pUIYnZaCcHJPiMmh4A3/thumbnails_256/409b5290-38aa-4530-9f92-259be4940637.jpg';

async function checkFile() {
  try {
    const [exists] = await bucket.file(filePath).exists();
    console.log(`File ${filePath} exists: ${exists}`);
    
    if (exists) {
      const [metadata] = await bucket.file(filePath).getMetadata();
      console.log('Metadata:', JSON.stringify(metadata, null, 2));
    }
    
    // Also check the original file
    const origPath = 'photos/gPj2AYpX7pUIYnZaCcHJPiMmh4A3/409b5290-38aa-4530-9f92-259be4940637.jpg';
    const [origExists] = await bucket.file(origPath).exists();
    console.log(`Original file ${origPath} exists: ${origExists}`);
    
    console.log('Bucket name:', bucket.name);
  } catch (err) {
    console.error('Error:', err.message);
  }
}

checkFile();
