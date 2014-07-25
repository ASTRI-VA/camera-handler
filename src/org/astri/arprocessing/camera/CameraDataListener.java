package org.astri.arprocessing.camera;

public interface CameraDataListener {

	public void receiveCameraFrame(byte[] data, int width, int height, boolean backCamera);
	public void receivePhotoFrame(byte[] data, int width, int height);
	
}
