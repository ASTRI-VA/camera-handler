package org.astri.arprocessing.camera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

public class CameraHandler {

	private static final String TAG = "CameraHandler";

	public static final int DEFAULT_FRAME_WIDTH = 640;
	public static final int DEFAULT_FRAME_HEIGHT = 480;
	
	private static int FrameWidth = DEFAULT_FRAME_WIDTH;
	private static int FrameHeight = DEFAULT_FRAME_HEIGHT;
	
	private SurfaceHolder previewHolder = null;
	private Camera camera;
	private CameraInfo cameraInfo = new CameraInfo();
	private boolean inPreview;
	private int currentCameraFacing = CameraInfo.CAMERA_FACING_BACK;

	/**
	 * Buffer for camera driver to store preview data, created statically so
	 * that it doesn't need to be re-allocated each frame.
	 */
	private byte[] mPreviewBuffer = null;
	private byte[] mPreviewBuffer2 = null;
	private byte[] mPreviewBuffer3 = null;
	private byte[] mPreviewBuffer4 = null;
	private int mPreviewBufferIdx;

	private int mPreviewWidth;
	private int mPreviewHeight;

	private CameraDataListener dataListener;
	
	private PhotoTaker photoTaker;
	private PhotoTaker markerTaker;

	private int displayWidth;
	private int displayHeight;
	
	private String preferredFocusMode = null;
	
	public CameraHandler(Context context) {
		this(CameraInfo.CAMERA_FACING_BACK, context);
	}
	
	public CameraHandler(int cameraFacing, Context context) {
		this(DEFAULT_FRAME_WIDTH, DEFAULT_FRAME_HEIGHT,
				cameraFacing, context);
	}
	
	public CameraHandler(int frameWidth, int frameHeight, int cameraFacing, Context context) {
		
		CameraHandler.FrameWidth = frameWidth;
		CameraHandler.FrameHeight = frameHeight;
		this.currentCameraFacing = cameraFacing;
		
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		displayWidth = size.y;
		displayHeight = size.x;
		
	}

	public Camera getCamera() {
		return camera;
	}
	
	public CameraInfo getCameraInfo() {
		return cameraInfo;
	}
	
	public Point getFrameSize() {
		return new Point(FrameWidth, FrameHeight);
	}
	
	public int getCameraNumber() {
		return Camera.getNumberOfCameras();
	}
	
	public void setPreviewHolder(SurfaceView preview) {
		previewHolder = preview.getHolder();
		previewHolder.addCallback(surfaceCallback);
	}

	public void setDataListener(CameraDataListener listener) {
		this.dataListener = listener;
		photoTaker = new PhotoTaker(photoListener, PhotoTaker.PHOTO_ASPECT_RATIO, 
				PhotoTaker.PHOTO_ASPECT_RATIO_LIMIT, PhotoTaker.PHOTO_MAX_PIXELS);
		markerTaker = new PhotoTaker(markerListener, PhotoTaker.MARKER_ASPECT_RATIO,
				PhotoTaker.MARKER_ASPECT_RATIO_LIMIT, PhotoTaker.MARKER_MAX_PIXELS);
	}
	
	public void setPreviewSize(int presetWidth, int presetHeight) {
		FrameWidth = presetWidth;
		FrameHeight = presetHeight;
		photoTaker.setAspectRatio ( FrameWidth * 1.0f / FrameHeight );
	}

	public void setPreferredFocusMode(String focusMode) {
		this.preferredFocusMode = focusMode;
	}
	
	public int[] resumeCameraIndex(int cameraIndex) {
		
	    try {
	        camera = Camera.open(cameraIndex);
	    } catch (RuntimeException e) {
	    	Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
	    }
		return resumeCamera();
	}
	
	public int[] resumeCamera(int cameraFacing) {
		currentCameraFacing = cameraFacing;
		openCameraFacing(cameraFacing);
		return resumeCamera();
		
	}
	
	private void openCameraFacing(int cameraFacing) {
		
		int cameraCount = Camera.getNumberOfCameras();
		// Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
	    for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
	        Camera.getCameraInfo(camIdx, cameraInfo);
	        if (cameraInfo.facing == cameraFacing) {
	            try {
	                camera = Camera.open(camIdx);
	            } catch (RuntimeException e) {
	                Log.e(TAG, "Camera failed to open: " + e.getLocalizedMessage());
	            }
	            break;
	        }
	    }
	}
	
	public int[] resumeCamera() {
		
		// no preferred camera or selected camera failed to open, try other cameras
		int cameraCount = Camera.getNumberOfCameras();
		if(camera == null){
			Log.e(TAG, "Selected Camera open returns null, trying to open other cameras, camera count: " + cameraCount);
			for(int i = 0; i < cameraCount; i++){
				camera = Camera.open(i);
				if(camera != null){
					Log.d(TAG, "Camera opened at id: " + i);
					break;
				}
			}
		}
		
		int [] frameSize = new int [2];
		if (camera != null) {
			initCamera(FrameWidth, FrameHeight);
			Log.d(TAG, "finished camera init");
			try {
				camera.setPreviewDisplay(previewHolder);
			} catch (IOException e) {
				Log.e(TAG, "Error setting camera preview display", e);
			}
			camera.startPreview();
			Log.d(TAG, "Camera preview started");
			frameSize[0] = FrameWidth;
			frameSize[1] = FrameHeight;
			return frameSize;
		} else {
			Log.e(TAG, "Failed to open camera");

			frameSize[0] = 0;
			frameSize[1] = 0;
		}
		
		return frameSize;
	}

	private void initCamera(int presetWidth, int presetHeight) {

		Camera.Parameters parameters = camera.getParameters();
		int minDifference = 100000;

		boolean isRotatedNeeded = false;
		if (presetWidth < presetHeight) {
			isRotatedNeeded = true;
			int tmp = presetWidth;
			presetWidth = presetHeight;
			presetHeight = tmp;
		}
		List<Size> previewSizes = parameters.getSupportedPreviewSizes();
		Log.d(TAG, "Supported preview sizes:" + previewSizes.size());
		float minRatioDifference = 1000000.0f;
		for (Size s : previewSizes) {
			float ratioDifference = Math.abs(s.height * 1.0f / s.width - presetHeight * 1.0f / presetWidth);
			int heightDifference = Math.abs(s.height-presetHeight);
			if (ratioDifference < minRatioDifference || (ratioDifference == minRatioDifference && heightDifference < minDifference) ) {
				Log.d(TAG, "preview size w: " + s.width + ", h:" + s.height);
				minRatioDifference = ratioDifference;
				minDifference = heightDifference;
				FrameWidth = s.width;
				FrameHeight = s.height;
				
			}
//			int heightDifference = Math.abs(s.height-presetHeight);
//			if (s.width == presetWidth && heightDifference < minDifference) {
//				Log.d(TAG, "preview size w: " + s.width + ", h:" + s.height);
//				minDifference = heightDifference;
//				FrameWidth = s.width;
//				FrameHeight = s.height;
//			}
		}
		
		if(photoTaker != null) {
			photoTaker.setPictureSize(camera);
		}
		if (isRotatedNeeded) {
			camera.setDisplayOrientation(90);
		}
		parameters.setPreviewSize(FrameWidth, FrameHeight);
		parameters.setPreviewFormat(ImageFormat.NV21);
		parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

		String focusMode = chooseFocusMode(parameters);
		if(focusMode != null) {
			parameters.setFocusMode(focusMode);
		}
		camera.setParameters(parameters);
		Log.d(TAG, "finished set camera parameters");

		setCallback();
	}
	
	private String chooseFocusMode(Parameters parameters) {
		
		String selectedFocusMode = null;
		
		List<String> supportedFocusModes = parameters.getSupportedFocusModes();

		if (supportedFocusModes != null) {
			
			if(preferredFocusMode != null && 
					supportedFocusModes.contains(preferredFocusMode)){
				selectedFocusMode = preferredFocusMode;
			}
			else if (supportedFocusModes.contains(
						Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
				selectedFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
			}
			else if (supportedFocusModes.contains(
						Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
				selectedFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
			} 
			else if (supportedFocusModes.contains(
						Camera.Parameters.FOCUS_MODE_AUTO)) {
				selectedFocusMode = Camera.Parameters.FOCUS_MODE_AUTO;
			}
			/*
			if (supportedFocusModes
					.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
				Log.d(TAG, "Set focus mode INFINITY");
			} else if (supportedFocusModes
					.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
				Log.d(TAG, "Set focus mode FIXED");
			}
			*/
		}
		
		Log.d(TAG, "Selected focus mode: " + selectedFocusMode);
		return selectedFocusMode;
	}
	
	/**
	 * Init photo size using back camera
	 */
	public void initPhotoSize() {
		initPhotoSize(CameraInfo.CAMERA_FACING_BACK);
	}
	
	/**
	 * Init photo size for taking photo by either front or back camera
	 * @param cameraFacing
	 */
	public void initPhotoSize(int cameraFacing){
		
		if(camera != null){
			// camera already opened
			photoTaker.setPictureSize(camera);
		}
		else {
			openCameraFacing(cameraFacing);
			if(camera != null) {
				photoTaker.setPictureSize(camera);
				camera.release();
				camera = null;
			} else {
				Log.e(TAG, "Photo size init failed");
			}
		}
		
	}
	
	public int getPhotoWidth(){
		return photoTaker.getPhotoWidth();
	}
	
	public int getPhotoHeight(){
		return photoTaker.getPhotoHeight();
	}
	
	public void pauseCamera() {
		if (camera != null) {
			if (inPreview) {
				camera.setPreviewCallbackWithBuffer(null);
				inPreview = false;
				camera.stopPreview();
			}

			camera.release();
			camera = null;
		}
		inPreview = false;
	}
	
	public boolean takePhoto(){
		Log.d(TAG, "starting to take photo");
		
		if(inPreview){
			photoTaker.takePhoto(camera);
			Log.d(TAG, "photo taking called");
			return true;
		} else {
			Log.e(TAG, "Can not take a photo now!");
			return false;
		}
	}
	
	private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				Log.w(TAG, "surface created");
				if (camera != null) {
					camera.setPreviewDisplay(previewHolder);
				}
			} catch (Throwable t) {
				Log.e("PreviewDemo-surfaceCallback",
						"Exception in setPreviewDisplay()", t);
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Log.w(TAG, "surface changed");
			if (camera == null) {
				return;
			}

			Camera.Parameters parameters = camera.getParameters();
			List<Size> sizes = parameters.getSupportedPreviewSizes();
			Log.d(TAG, "Supported preview sizes:");
			for (Size s : sizes) {
				Log.d(TAG, "w: " + s.width + ", h:" + s.height);
			}

			if (FrameWidth > FrameHeight) {
				parameters.setPreviewSize(FrameWidth, FrameHeight);
			} else {
				parameters.setPreviewSize(FrameHeight, FrameWidth);
				camera.setDisplayOrientation(90);
			}
			camera.setParameters(parameters);
			camera.startPreview();
			inPreview = true;

		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// no-op
			Log.w(TAG, "surface destroyed");

		}
	};

	/**
	 * Sets the Camera preview callback
	 * 
	 */
	public void setCallback() {
		int bufferSize = 0;
		int pformat;
		int bitsPerPixel;

		pformat = camera.getParameters().getPreviewFormat();

		// Get pixel format information to compute buffer size.
		PixelFormat info = new PixelFormat();
		PixelFormat.getPixelFormatInfo(pformat, info);
		bitsPerPixel = info.bitsPerPixel;

		mPreviewWidth = camera.getParameters().getPreviewSize().width;
		mPreviewHeight = camera.getParameters().getPreviewSize().height;

		Log.d(TAG, "preview w: " + mPreviewWidth + ", h: " + mPreviewHeight);

		bufferSize = mPreviewWidth * mPreviewHeight * bitsPerPixel / 8;

		// Make sure buffer is deleted before creating a new one.
		mPreviewBuffer = null;
		mPreviewBuffer2 = null;
		mPreviewBuffer3 = null;
		mPreviewBuffer4 = null;

		// New preview buffer.
		mPreviewBuffer = new byte[bufferSize + 4096];
		mPreviewBuffer2 = new byte[bufferSize + 4096];
		mPreviewBuffer3 = new byte[bufferSize + 4096];
		mPreviewBuffer4 = new byte[bufferSize + 4096];

		Log.d(TAG, "Add callback buffer");
		inPreview = true;

		// with buffer requires addbuffer.
		camera.addCallbackBuffer(mPreviewBuffer);
		mPreviewBufferIdx = 1;
		camera.setPreviewCallbackWithBuffer(mCameraCallback);

	}

	/**
	 * Camera callback to retrieve camera frames.
	 * 
	 */
	private final Camera.PreviewCallback mCameraCallback = new Camera.PreviewCallback() {
		/**
		 * Actual callback function for camera frames. Does per frame
		 * processing.
		 * 
		 * @param data
		 *            buffer for preview data, in YUV420sp format.
		 * @param c
		 *            Camera object.
		 */
		public void onPreviewFrame(byte[] data, Camera c) {
			if (c != null) {
				// with buffer requires addbuffer each callback frame.
				switch (mPreviewBufferIdx % 4) {
				case 0:
					c.addCallbackBuffer(mPreviewBuffer);
					break;
				case 1:
					c.addCallbackBuffer(mPreviewBuffer2);
					break;
				case 2:
					c.addCallbackBuffer(mPreviewBuffer3);
					break;
				case 3:
					c.addCallbackBuffer(mPreviewBuffer4);
					break;
				}
				;
				mPreviewBufferIdx++;
				if (inPreview) {
					c.setPreviewCallbackWithBuffer(this);
				}
			}
			
			if (dataListener != null) {
			dataListener.receiveCameraFrame(data, FrameWidth, FrameHeight, 
					currentCameraFacing == CameraInfo.CAMERA_FACING_BACK);
			}
			//Log.d(TAG, "frame received from camera");
		}
	};
	
	public void switchCamera(){
		
		if(camera != null){
			if(Camera.getNumberOfCameras() > 1){
				
				pauseCamera();
				
				if(currentCameraFacing == CameraInfo.CAMERA_FACING_BACK){
					currentCameraFacing = CameraInfo.CAMERA_FACING_FRONT;
				} else {
					currentCameraFacing = CameraInfo.CAMERA_FACING_BACK;
				}
				
				resumeCamera(currentCameraFacing);
			}
		}
		
	}
	
	public void focusOnTouch(float x, float y) {
		
	    if (camera != null) {

	        camera.cancelAutoFocus();
	        Rect focusRect = calculateFocusArea(x, y, 1f);

	        Parameters parameters = camera.getParameters();
	        parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
	        
	        List<Camera.Area> areas = new ArrayList<Camera.Area>();
	        areas.add(new Camera.Area(focusRect, 1000));
	        Log.d(TAG, "touch x: " + x + " y: " + y + 
	        		 ", focus x: " + focusRect.centerX() + " y: " + focusRect.centerY() + 
	        		 ", disp w: " + displayWidth + " h: " + displayHeight);
	        parameters.setFocusAreas(areas);

	        camera.setParameters(parameters);
	        camera.autoFocus(focusCallback);
	    }
	    
	}
	
	private Camera.AutoFocusCallback focusCallback = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			Log.d(TAG, "camera focused: " + success);
		}
	};
	
	float focusAreaSize = 72f;
	
	// Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
	private Rect calculateFocusArea(float x, float y, float coefficient) {
	    int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
	    
	    float xScaled = x * (2000f / (float)displayWidth) - 1000f;
	    float yScaled = y * (2000f / (float)displayHeight) - 1000f;
	    
	    int left = clamp((int) xScaled - areaSize / 2, -1000, 1000 - areaSize);
	    int top = clamp((int) yScaled - areaSize / 2, -1000, 1000 - areaSize);

	    RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
	    
	    return new Rect(Math.round(rectF.left), Math.round(rectF.top), 
	    		Math.round(rectF.right), Math.round(rectF.bottom));
	}

	private int clamp(int x, int min, int max) {
	    if (x > max) {
	        return max;
	    }
	    if (x < min) {
	        return min;
	    }
	    return x;
	}
	
	
	public boolean captureMarker(float aspectRatio){
		Log.d(TAG, "starting to capture marker");
		
		if(inPreview){
			markerTaker.setAspectRatio(aspectRatio);
			markerTaker.takePhoto(camera);
			Log.d(TAG, "marker taking called");
			return true;
		} else {
			Log.e(TAG, "Can not capture marker now!");
			return false;
		}
		
	}
	
	private PhotoListener photoListener = new PhotoListener() {
		@Override
		public void photoCaptured(byte[] data, int width, int height, float screenAspectRatio) {

			if (dataListener != null)
			dataListener.receivePhotoFrame(data, width, height);
		}
	};
	
	private PhotoListener markerListener = new PhotoListener() {
		@Override
		public void photoCaptured(byte[] data, int width, int height, float screenAspectRatio) {

			if (dataListener != null)
			dataListener.receiveMarkerFrame(data, width, height, screenAspectRatio);
		}
	};
	
}
