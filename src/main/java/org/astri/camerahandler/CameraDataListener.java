package org.astri.camerahandler;

public interface CameraDataListener {

	public void receiveCameraFrame(byte[] data, int width, int height, boolean backCamera, int imageFormat);
	public void receivePhotoFrame(byte[] data, int width, int height);
	public void receiveMarkerFrame(byte[] data, int width, int height, float screenAspectRatio);
	
}
