package org.astri.arprocessing.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.util.Log;

public class PhotoTaker {

	public static final float PHOTO_ASPECT_RATIO = 1.3333f;
	public static final float PHOTO_ASPECT_RATIO_LIMIT = 0.01f;
	public static final int PHOTO_MAX_PIXELS = 2048 * 1600;
	
	public static final float MARKER_ASPECT_RATIO = 1.3333f;
	public static final float MARKER_ASPECT_RATIO_LIMIT = 0.1f;
	public static final int MARKER_MAX_PIXELS = 1280 * 960;
	
	
	private static final String TAG = "PhotoTaker";
	
	private float aspectRatio;
	private float aspectRatioThreshold;
	private int maxPixels;
	
	private boolean takingPhoto = false;
	private int photoWidth;
	private int photoHeight;
	
	private PhotoListener photoListener;
	
	PhotoTaker(PhotoListener photoListener, float aspectRatio, float aspectRatioThreshold, int maxPixels){
		this.photoListener = photoListener;
		this.aspectRatio = aspectRatio;
		this.aspectRatioThreshold = aspectRatioThreshold;
		this.maxPixels = maxPixels;
	}
	
	public void setAspectRatio(float newRatio){
		aspectRatio = newRatio;
	}
	
	public boolean takePhoto(Camera camera){
		
		if(camera != null){
			
			setPictureSize(camera);
			
			takingPhoto = true;
			camera.autoFocus(focusCallback);
			Log.d(TAG, "photo taking finished");
			return true;
		} else {
			Log.e(TAG, "Can not take a photo now!");
			return false;
		}
		
	}
	
	public void setPictureSize(Camera camera){
		
		Camera.Parameters parameters = camera.getParameters();
		
		List<Size> pictureSizes = parameters.getSupportedPictureSizes();
		Collections.sort(pictureSizes, new SizeComparator());
		Size bestSize = null;

		Log.d(TAG, "Supported picture sizes:");
		for (Size s : pictureSizes) {
			Log.d(TAG, "picture size w: " + s.width + ", h:" + s.height);
			if(s.width * s.height > maxPixels){
				continue; // too big size
			}
			float currentAspectRatio = (float)s.width / (float)s.height;
			float difference = Math.abs(aspectRatio - currentAspectRatio);
			if(difference < aspectRatioThreshold){
				bestSize = s;
				break;
			}
		}
		
		if(bestSize == null){
			// just try 640 x 480
			photoWidth = 640;
			photoHeight = 480;
		} else {
			photoWidth = bestSize.width;
			photoHeight = bestSize.height;
		}
		
		Log.d(TAG, "set picture size w: " + photoWidth + " h: " + photoHeight);
		
		parameters.setPictureSize(photoWidth, photoHeight);
		camera.setParameters(parameters);
	}
	
	public int getPhotoWidth(){
		return photoWidth;
	}
	
	public int getPhotoHeight(){
		return photoHeight;
	}
	
	private Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			Log.d(TAG, "camera focused: " + success);
			if(takingPhoto){
				camera.takePicture(null, null, jpegCallback);
				takingPhoto = false;
			}
			else {
				//camera.cancelAutoFocus();
			}
		}
	};
	
	private Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "image data received: " + data);
			camera.startPreview();
			Log.d(TAG, "Picture taken, restarting preview");
			photoListener.photoCaptured(data, photoWidth, photoHeight, aspectRatio);
		}
	};
	
	private Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			
			//saveJpeg(data);
			
			// decode jpeg to byte array and convert to RGB565
			ByteBuffer buffer = decodeJpegToRGB565(data);
			
			camera.startPreview();
			camera.cancelAutoFocus();
			Log.d(TAG, "Picture taken, restarting preview. w: " + photoWidth + " h:" + photoHeight);
			
			//previewHolder.addCallback(surfaceCallback);
			//camera.setPreviewDisplay(previewHolder);
			
			photoListener.photoCaptured(buffer.array(), photoWidth, photoHeight, aspectRatio);
		}
	};
	
	private ByteBuffer decodeJpegToRGB565(byte[] data){
		
		// decode jpeg to byte array and convert to RGB565
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.RGB_565;
		Bitmap imageBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
		int bytes = imageBitmap.getRowBytes() * imageBitmap.getHeight();
		
		Log.d(TAG, "data length: " + data.length + " bytes: " + 
				bytes + " imgh: " + imageBitmap.getHeight());
		
		ByteBuffer buffer = ByteBuffer.allocate(bytes);
		imageBitmap.copyPixelsToBuffer(buffer);
		
		return buffer;
	}
	
	
	// Use this method for debugging jpeg images
	private void saveJpeg(byte[] data){
		
		File sd = Environment.getExternalStorageDirectory();
		String filePath = sd.getPath();
	    File file = new File("/sdcard/Android/jpgmarker.jpg");
	    FileOutputStream os;
	    try {
	        os = new FileOutputStream(file, true);
	        os.write(data);
	        os.close();
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	    	//photoTaken.release();
	    }
		
	}
	
}
