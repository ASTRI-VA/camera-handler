package org.astri.camerahandler;

public interface CameraDataListener {

    void receiveCameraFrame(byte[] data, int width, int height,
							boolean backCamera, int imageFormat, int imageOrientation);
    void receivePhotoFrame(byte[] data, int width, int height);
    void receiveMarkerFrame(byte[] data, int width, int height, float screenAspectRatio);

}
