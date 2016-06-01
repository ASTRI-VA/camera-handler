package org.astri.camerahandler;

import java.util.Comparator;

import android.hardware.Camera;
import android.hardware.Camera.Size;

public class SizeComparator implements Comparator<Camera.Size> {

	@Override
	public int compare(Size lhs, Size rhs) {
		
		Integer lpixels = lhs.width * lhs.height;
		Integer rpixels = rhs.width * rhs.height;
		
		return rpixels.compareTo(lpixels);
	}

}
