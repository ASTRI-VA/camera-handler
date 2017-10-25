# Android Camera Handler

This is a utility library for getting raw camera frames from Android camera.

The frames are received as byte arrays which can be passed on to processing and later rendered to a surface view.

The library also contains following additional features:
* High resuloution photo capture during frame capture
* Individual low resolution frame capture during frame capture

By default on most devices the raw frame data is in YUV format, so the frames have to be separately converted to RGB if needed.
