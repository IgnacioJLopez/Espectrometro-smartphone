package com.example.android.camera2.basic.fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.navigation.NavArgs;
import java.lang.IllegalArgumentException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.HashMap;

public class AutoRotateFragmentArgs implements NavArgs {
  private final HashMap arguments = new HashMap();

  private AutoRotateFragmentArgs() {
  }

  private AutoRotateFragmentArgs(HashMap argumentsMap) {
    this.arguments.putAll(argumentsMap);
  }

  @NonNull
  @SuppressWarnings("unchecked")
  public static AutoRotateFragmentArgs fromBundle(@NonNull Bundle bundle) {
    AutoRotateFragmentArgs __result = new AutoRotateFragmentArgs();
    bundle.setClassLoader(AutoRotateFragmentArgs.class.getClassLoader());
    if (bundle.containsKey("camera_id")) {
      String cameraId;
      cameraId = bundle.getString("camera_id");
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      __result.arguments.put("camera_id", cameraId);
    } else {
      throw new IllegalArgumentException("Required argument \"camera_id\" is missing and does not have an android:defaultValue");
    }
    if (bundle.containsKey("pixel_format")) {
      int pixelFormat;
      pixelFormat = bundle.getInt("pixel_format");
      __result.arguments.put("pixel_format", pixelFormat);
    } else {
      throw new IllegalArgumentException("Required argument \"pixel_format\" is missing and does not have an android:defaultValue");
    }
    return __result;
  }

  @SuppressWarnings("unchecked")
  @NonNull
  public String getCameraId() {
    return (String) arguments.get("camera_id");
  }

  @SuppressWarnings("unchecked")
  public int getPixelFormat() {
    return (int) arguments.get("pixel_format");
  }

  @SuppressWarnings("unchecked")
  @NonNull
  public Bundle toBundle() {
    Bundle __result = new Bundle();
    if (arguments.containsKey("camera_id")) {
      String cameraId = (String) arguments.get("camera_id");
      __result.putString("camera_id", cameraId);
    }
    if (arguments.containsKey("pixel_format")) {
      int pixelFormat = (int) arguments.get("pixel_format");
      __result.putInt("pixel_format", pixelFormat);
    }
    return __result;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
        return true;
    }
    if (object == null || getClass() != object.getClass()) {
        return false;
    }
    AutoRotateFragmentArgs that = (AutoRotateFragmentArgs) object;
    if (arguments.containsKey("camera_id") != that.arguments.containsKey("camera_id")) {
      return false;
    }
    if (getCameraId() != null ? !getCameraId().equals(that.getCameraId()) : that.getCameraId() != null) {
      return false;
    }
    if (arguments.containsKey("pixel_format") != that.arguments.containsKey("pixel_format")) {
      return false;
    }
    if (getPixelFormat() != that.getPixelFormat()) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + (getCameraId() != null ? getCameraId().hashCode() : 0);
    result = 31 * result + getPixelFormat();
    return result;
  }

  @Override
  public String toString() {
    return "AutoRotateFragmentArgs{"
        + "cameraId=" + getCameraId()
        + ", pixelFormat=" + getPixelFormat()
        + "}";
  }

  public static class Builder {
    private final HashMap arguments = new HashMap();

    public Builder(AutoRotateFragmentArgs original) {
      this.arguments.putAll(original.arguments);
    }

    public Builder(@NonNull String cameraId, int pixelFormat) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      this.arguments.put("pixel_format", pixelFormat);
    }

    @NonNull
    public AutoRotateFragmentArgs build() {
      AutoRotateFragmentArgs result = new AutoRotateFragmentArgs(arguments);
      return result;
    }

    @NonNull
    public Builder setCameraId(@NonNull String cameraId) {
      if (cameraId == null) {
        throw new IllegalArgumentException("Argument \"camera_id\" is marked as non-null but was passed a null value.");
      }
      this.arguments.put("camera_id", cameraId);
      return this;
    }

    @NonNull
    public Builder setPixelFormat(int pixelFormat) {
      this.arguments.put("pixel_format", pixelFormat);
      return this;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public String getCameraId() {
      return (String) arguments.get("camera_id");
    }

    @SuppressWarnings("unchecked")
    public int getPixelFormat() {
      return (int) arguments.get("pixel_format");
    }
  }
}
