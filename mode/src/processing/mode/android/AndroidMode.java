/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2011-12 Ben Fry and Casey Reas

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.mode.android;

import processing.app.*;
import processing.app.ui.Editor;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.core.PApplet;
import processing.mode.android.AndroidSDK.CancelException;
import processing.mode.java.JavaMode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Future;

/** 
 * Programming mode to create and run Processing sketches on Android devices.
 */
public class AndroidMode extends JavaMode {
  private AndroidSDK sdk;
  private File coreZipLocation;
  private AndroidRunner runner;
  
  private boolean showWatchFaceDebugMessage = true;
  private boolean showWatchFaceSelectMessage = true;
  private boolean showWallpaperSelectMessage = true;
  
  private boolean checkingSDK = false;
  private boolean userCancelledSDKSearch = false;
  
  // Using this temporarily until support for mode translations is finalized in the Processing app
  private static Map<String, String> textStrings = null;

  private static final String BLUETOOTH_DEBUG_URL = 
      "https://developer.android.com/training/wearables/apps/debugging.html";
    
  private static final String DISTRIBUTING_APPS_TUT_URL = 
      "http://android.processing.org/tutorials/distributing/index.html";  
  
  public AndroidMode(Base base, File folder) {
    super(base, folder);
    loadTextStrings();
  }


  @Override
  public Editor createEditor(Base base, String path,
                             EditorState state) throws EditorException {
    return new AndroidEditor(base, path, state, this);
  }


  @Override
  public String getTitle() {
    return "Android";
  }


  public File[] getKeywordFiles() {
    return new File[] {
      Platform.getContentFile("modes/java/keywords.txt")
    };
  }


  public File[] getExampleCategoryFolders() {
    return new File[] {
      new File(examplesFolder, "Basics"),
      new File(examplesFolder, "Topics"),
      new File(examplesFolder, "Demos"),
      new File(examplesFolder, "Sensors")
    };
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /** @return null so that it doesn't try to pass along the desktop version of core.jar */
  public Library getCoreLibrary() {
    return null;
  }


  protected File getCoreZipLocation() {
    if (coreZipLocation == null) {
      /*
      // for debugging only, check to see if this is an svn checkout
      File debugFile = new File("../../../android/core.zip");
      if (!debugFile.exists() && Base.isMacOS()) {
        // current path might be inside Processing.app, so need to go much higher
        debugFile = new File("../../../../../../../android/core.zip");
      }
      if (debugFile.exists()) {
        System.out.println("Using version of core.zip from local SVN checkout.");
//        return debugFile;
        coreZipLocation = debugFile;
      }
      */

      // otherwise do the usual
      //    return new File(base.getSketchbookFolder(), ANDROID_CORE_FILENAME);
      coreZipLocation = getContentFile("processing-core.zip");
    }
    return coreZipLocation;
  }

  
  public void resetUserSelection() {
    userCancelledSDKSearch = false;
  }
  
  
  public void checkSDK(Editor editor) {    
    if (checkingSDK) {
      // Some other thread has invoked SDK checking, so wait until the first one
      // is done (it might involve downloading the SDK, etc).
      while (checkingSDK) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { 
          return;
        }      
      }
    }
    if (userCancelledSDKSearch) return;
    checkingSDK = true;
    Throwable tr = null;
    Boolean broken = false;
    if (sdk != null) { //when mode changes, sdk object is not recreated, this ensures that
      try {
        sdk = new AndroidSDK(sdk.getSdkFolder());
      } catch (AndroidSDK.BadSDKException | IOException e) {
        Messages.showWarning(AndroidMode.getTextString("android_mode.warn.cannot_load_sdk_title"),
                AndroidMode.getTextString("android_mode.warn.broken_sdk_folder",e.getMessage()));
        broken = true;
      }
    }
    if (sdk == null || broken) {
      try {
        sdk = AndroidSDK.load(true, editor);
        if (sdk == null) {
          sdk = AndroidSDK.locate(editor, this);
        }
      } catch (CancelException cancel) {
        userCancelledSDKSearch = true;
        tr = cancel;
      } catch (Exception other) {
        tr = other;
      }
    }
    if (sdk == null) {
      Messages.showWarning(AndroidMode.getTextString("android_mode.warn.cannot_load_sdk_title"),
              AndroidMode.getTextString("android_mode.warn.cannot_load_sdk_body",tr.getMessage()), tr);
    } else {
      Devices devices = Devices.getInstance();
      devices.setSDK(sdk);
    }
    checkingSDK = false;
  }


  public AndroidSDK getSDK() {
    return sdk;
  }
  
  
  @Override
  public String getSearchPath() {
    if (sdk == null) {
        checkSDK(null);
    }

    if (sdk == null) {
      Messages.log(AndroidMode.getTextString("android_mode.info.cannot_open_sdk_path"));
      return "";
    }
    
    String coreJarPath = new File(getFolder(), "processing-core.zip").getAbsolutePath();
    return sdk.getAndroidJarPath().getAbsolutePath() + File.pathSeparatorChar + coreJarPath;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd.HHmm");


  static public String getDateStamp() {
    return dateFormat.format(new Date());
  }


  static public String getDateStamp(long stamp) {
    return dateFormat.format(new Date(stamp));
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void handleRunEmulator(Sketch sketch, AndroidEditor editor, 
      RunnerListener listener, String avdName) throws SketchException, IOException, CancelException {

    //check for emulator
    System.out.println("Checking Emulator....");
    String imageName = null;
    boolean checkEmulator = EmulatorController.getInstance(false).emulatorExists(sdk);
    if (!checkEmulator) {
      String[] options = new String[] { Language.text("prompt.yes"), Language.text("prompt.no") };
      String message = AndroidMode.getTextString("android_sdk.dialog.install_emu_body");
      String title = AndroidMode.getTextString("android_sdk.dialog.install_emu_title");
      int result = JOptionPane.showOptionDialog(null, message, title,
              JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
              null, options, options[0]);
      if (result == JOptionPane.YES_OPTION) {
        SDKDownloader downloader = new SDKDownloader(editor, SDKDownloader.DOWNLOAD_EMU);
        if (downloader.cancelled()) {
          throw new CancelException(AndroidMode.getTextString("android_sdk.error.emulator_download_canceled"));
        }
        try {
          AVD.downloadDefaultImage(sdk, editor, this);
        } catch (Error e){
          throw new CancelException(AndroidMode.getTextString("sys_image_downloader.download_failed_message"));
        }
      }
    }


    Vector<Vector<String>> existingImages = AVD.listImages(sdk,false);
    boolean checkSysImage = existingImages.isEmpty();
    //this is to install image in cases when emulator alone is installed
    System.out.println("Checking System Image....");
    try {
      if (existingImages.isEmpty()) {
        Messages.showMessage(AndroidMode.getTextString("sys_image_downloader.missing_image_title")
                , AndroidMode.getTextString("sys_image_downloader.missing_image_body"));
        imageName = AVD.downloadDefaultImage(sdk, editor, this);
      }
      else{
        Vector<String> target = existingImages.get(0);
        imageName = "system-images;"+target.get(0)+";"+target.get(1)+";"+target.get(2);
      }
    } catch (Error e){
      throw new CancelException(AndroidMode.getTextString("sys_image_downloader.download_failed_message"));
    }

    //Check if atleast one AVD already exists :
    boolean firstAVD;
    if(avdName !=null)
      firstAVD = avdName.isEmpty();
    else firstAVD = true;

    //Check if previously selected AVD exits :
    if(!AVD.exists(sdk, avdName)) {
      int result = AVD.showEmulatorNotFoundDialog();
      if (result == JOptionPane.YES_OPTION) {
        firstAVD = true;
      } else {
        throw new CancelException(AndroidMode.getTextString("android_avd.error.selected_emu_not_found_title"));
      }
    }

    //if first, then create a new AVD
    if (firstAVD) {
      System.out.println("Creating Default AVD....");
      avdName = "processing_phone";
      AVD newAvd = new AVD(avdName,"Nexus One",imageName);
      boolean result = newAvd.create(sdk);
      if(!result){
        throw new CancelException(AndroidMode.getTextString("android_avd.error.cannot_create_avd_title"));
      }
    }

    Preferences.set("android.emulator.avd.name",avdName);

    listener.startIndeterminate();
    listener.statusNotice(AndroidMode.getTextString("android_mode.status.starting_project_build"));
    AndroidBuild build = new AndroidBuild(sketch, this, editor.getAppComponent());

    listener.statusNotice(AndroidMode.getTextString("android_mode.status.building_project"));
    build.build("debug");

    int comp = build.getAppComponent();

    //Check port and add if not present
    Integer portNumber = EmulatorController.getPort(avdName); //search ports
    if(portNumber==null || portNumber<0){ //if not found
      EmulatorController.addToPorts(avdName); // add to ports
    }
    Future<Device> emu = Devices.getInstance().getEmulator(build.isWear(),avdName);
    runner = new AndroidRunner(build, listener);
    runner.launch(emu, comp, true);
  }


  public void handleRunDevice(Sketch sketch, AndroidEditor editor, 
      RunnerListener listener)
    throws SketchException, IOException {    
    
    final Devices devices = Devices.getInstance();
    java.util.List<Device> deviceList = devices.findMultiple(false);
    if (deviceList.size() == 0) {
      Messages.showWarning(AndroidMode.getTextString("android_mode.dialog.no_devices_found_title"), 
                           AndroidMode.getTextString("android_mode.dialog.no_devices_found_body"));
      listener.statusError(AndroidMode.getTextString("android_mode.status.no_devices_found"));
      return;
    }
    
    listener.startIndeterminate();
    listener.statusNotice(AndroidMode.getTextString("android_mode.status.starting_project_build"));
    AndroidBuild build = new AndroidBuild(sketch, this, editor.getAppComponent());

    listener.statusNotice(AndroidMode.getTextString("android_mode.status.building_project"));
    File projectFolder = build.build("debug");
    if (projectFolder == null) {
      listener.statusError(AndroidMode.getTextString("android_mode.status.project_build_failed"));
      return;
    }
    
    int comp = build.getAppComponent();
    Future<Device> dev = Devices.getInstance().getHardware();
    runner = new AndroidRunner(build, listener);
    if (runner.launch(dev, comp, false)) {
      showPostBuildMessage(comp);
    }
  }

  
  public void showSelectComponentMessage(int appComp) {
    if (showWatchFaceDebugMessage && appComp == AndroidBuild.WATCHFACE) {
      AndroidUtil.showMessage(AndroidMode.getTextString("android_mode.dialog.watchface_debug_title"),
                              AndroidMode.getTextString("android_mode.dialog.watchface_debug_body", BLUETOOTH_DEBUG_URL));
      showWatchFaceDebugMessage = false;
    } 
  }
  
  
  public void showPostBuildMessage(int appComp) {
    if (showWallpaperSelectMessage && appComp == AndroidBuild.WALLPAPER) {
      AndroidUtil.showMessage(AndroidMode.getTextString("android_mode.dialog.wallpaper_installed_title"),
                              AndroidMode.getTextString("android_mode.dialog.wallpaper_installed_body"));
      showWallpaperSelectMessage = false;
    }
    if (showWatchFaceSelectMessage && appComp == AndroidBuild.WATCHFACE) {
      AndroidUtil.showMessage(AndroidMode.getTextString("android_mode.dialog.watchface_installed_title"),
                              AndroidMode.getTextString("android_mode.dialog.watchface_installed_body"));  
      showWatchFaceSelectMessage = false;
    } 
  }
  
  
  public void handleStop(RunnerListener listener) {
    listener.statusNotice("");
    listener.stopIndeterminate();

//    if (runtime != null) {
//      runtime.close();  // kills the window
//      runtime = null; // will this help?
//    }
    if (runner != null) {
      runner.close();
      runner = null;
    }
  }

  
  public boolean checkPackageName(Sketch sketch, int comp) {
    Manifest manifest = new Manifest(sketch, comp, getFolder(), false);
    String defName = Manifest.BASE_PACKAGE + "." + sketch.getName().toLowerCase();    
    String name = manifest.getPackageName();
    if (name.toLowerCase().equals(defName.toLowerCase())) {
      // The user did not set the package name, show error and stop
      AndroidUtil.showMessage(AndroidMode.getTextString("android_mode.dialog.cannot_export_package_title"),
                              AndroidMode.getTextString("android_mode.dialog.cannot_export_package_body", DISTRIBUTING_APPS_TUT_URL));
      return false;
    }
    return true;
  }
  
  
  public boolean checkAppIcons(Sketch sketch, int comp) {
    File sketchFolder = sketch.getFolder();

    File[] launcherIcons = AndroidUtil.getFileList(sketchFolder, AndroidBuild.SKETCH_LAUNCHER_ICONS, 
                                                                 AndroidBuild.SKETCH_OLD_LAUNCHER_ICONS);
    boolean allFilesExist = AndroidUtil.allFilesExists(launcherIcons);
    
    if (comp == AndroidBuild.WATCHFACE) {
      // Additional preview icons are needed for watch faces
      File[] watchFaceIcons = AndroidUtil.getFileList(sketchFolder, AndroidBuild.SKETCH_WATCHFACE_ICONS);      
      allFilesExist &= AndroidUtil.allFilesExists(watchFaceIcons);      
    }
    
    if (!allFilesExist) {
      // The user did not set custom icons, show error and stop
      AndroidUtil.showMessage(AndroidMode.getTextString("android_mode.dialog.cannot_use_default_icons_title"),
                              AndroidMode.getTextString("android_mode.dialog.cannot_use_default_icons_body", DISTRIBUTING_APPS_TUT_URL));
      return false;      
    }
    return true;
  }  
  
  
  public void initManifest(Sketch sketch, int comp) {
    new Manifest(sketch, comp, getFolder(), false);
  }  
  
  
  public void resetManifest(Sketch sketch, int comp) {
    new Manifest(sketch, comp, getFolder(), true);
  }
  
  private void loadTextStrings() {
    String baseFilename = "languages/mode.properties";
    File modeBaseFile = new File(getFolder(), baseFilename);
    if (textStrings == null) {
      textStrings = new HashMap<String, String>();
      String[] lines = PApplet.loadStrings(modeBaseFile);
      if (lines == null) {
        throw new NullPointerException("File not found:\n" + modeBaseFile.getAbsolutePath());
      }
      //for (String line : lines) {
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if ((line.length() == 0) ||
            (line.charAt(0) == '#')) continue;

        // this won't properly handle = signs inside in the text
        int equals = line.indexOf('=');
        if (equals != -1) {
          String key = line.substring(0, equals).trim();
          String value = line.substring(equals + 1).trim();

          value = value.replaceAll("\\\\n", "\n");
          value = value.replaceAll("\\\\'", "'");

          textStrings.put(key, value);
        }
      }      
    }
  }
  
  static public String getTextString(String key) {
    return textStrings.get(key);
//    return Language.text(key);
  }
  
  static public String getTextString(String key, Object... arguments) {
    String value = textStrings.get(key);
    if (value == null) {
      return key;
    }
    return String.format(value, arguments);
  }  
}