package org.astri.camerahandler;

public interface PhotoListener {

	public void photoCaptured(byte[] data, int width, int height, float screenAspectRatio);
	
}
