package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Inject implements IXposedHookZygoteInit {

    boolean loaded = false;
    boolean menuShown = false;
    String app_name = "com.example.package";
    String target_abi = Build.SUPPORTED_ABIS[0];
    String MODULE_PATH = null;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        XposedBridge.log("[YourCheat] Module path: " + MODULE_PATH);

        XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                null,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        String packageName = activity.getPackageName();

                        if (packageName.contains(app_name) && !menuShown) {
                            try {
                                XposedBridge.log("[YourCheat] Activity created: " + activity.getClass().getName());
                                XposedBridge.log("[YourCheat] Loading native library...");

                                if (!loaded) {
                                    for (int retry = 0; retry < 3; retry++) {
                                        try {
                                            loadNativeLibrary();
                                            loaded = true;
                                            XposedBridge.log("[YourCheat] Native library loaded successfully!");
                                            break;
                                        } catch (Exception e) {
                                            XposedBridge.log("[YourCheat] Load attempt " + (retry + 1) + " failed: " + e.getMessage());
                                            if (retry < 2) {
                                                Thread.sleep(500);
                                            }
                                        }
                                    }
                                    
                                    if (!loaded) {
                                        XposedBridge.log("[YourCheat] Native library failed to load after 3 attempts!");
                                        return;
                                    }
                                }

                                XposedBridge.log("[YourCheat] Showing menu with Activity context...");
                                showMenu(activity.getClassLoader(), activity);
                                menuShown = true;

                            } catch (Exception e) {
                                XposedBridge.log("[YourCheat] Activity hook error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        super.afterHookedMethod(param);
                    }
                }
        );
    }

    private void showMenu(ClassLoader gameCl, Context context) {
        try {
            XposedBridge.log("[YourCheat] === Starting Menu Display Process ===");
            XposedBridge.log("[YourCheat] Creating Menu instance...");

            if (context == null) {
                XposedBridge.log("[YourCheat] ERROR: Context is null!");
                return;
            }

            XposedBridge.log("[YourCheat] Context: " + context.getPackageName() + " (" + context.getClass().getSimpleName() + ")");

            ClassLoader moduleCl = Inject.class.getClassLoader();

            Class<?> menuClass = null;
            try {
                menuClass = XposedHelpers.findClass("com.android.support.Menu", moduleCl);
                XposedBridge.log("[YourCheat] ✓ Menu class found");
            } catch (Exception e) {
                XposedBridge.log("[YourCheat] ✗ FATAL: Menu class not found in module! " + e.getMessage());
                return;
            }

            Object menuInstance = null;
            try {
                XposedBridge.log("[YourCheat] Creating Menu instance with context...");
                menuInstance = XposedHelpers.newInstance(menuClass, context);
                XposedBridge.log("[YourCheat] ✓ Menu instance created");
            } catch (Throwable e) {
                XposedBridge.log("[YourCheat] ✗ CRITICAL: Menu constructor failed!");
                XposedBridge.log("[YourCheat]   Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());

                StackTraceElement[] trace = e.getStackTrace();
                for (int i = 0; i < Math.min(3, trace.length); i++) {
                    XposedBridge.log("[YourCheat]   at " + trace[i]);
                }
                
                return;
            }

            try {
                XposedBridge.log("[YourCheat] Setting window manager...");
                XposedHelpers.callMethod(menuInstance, "SetWindowManagerActivityOverlay");
                XposedBridge.log("[YourCheat] ✓ Window manager configured");
            } catch (Throwable e) {
                XposedBridge.log("[YourCheat] ✗ Window manager setup failed: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            try {
                XposedBridge.log("[YourCheat] Displaying menu...");
                XposedHelpers.callMethod(menuInstance, "ShowMenu");
                XposedBridge.log("[YourCheat] ✓ Menu displayed successfully!");
                XposedBridge.log("[YourCheat] === Menu Display Complete ===");
            } catch (Throwable e) {
                XposedBridge.log("[YourCheat] ✗ ShowMenu() failed: " + e.getMessage());
                e.printStackTrace();
                return;
            }

        } catch (Throwable e) {
            XposedBridge.log("[YourCheat] FATAL: Unexpected exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadNativeLibrary() throws Exception {
        try {
            String cachePath = "/data/user/0/" + app_name + "/cache/";
            String pathname = cachePath + "libMyLibName.so";
            File soFile = new File(pathname);

            File cacheDir = new File(cachePath);
            if (!cacheDir.exists()) cacheDir.mkdirs();
            if (soFile.exists()) soFile.delete();

            InputStream soFileStream = resourceStream("lib/" + target_abi + "/libMyLibName.so");
            if (soFileStream == null) {
                XposedBridge.log("[YourCheat] ERROR: libMyLibName.so not found in resources!");
                throw new Exception("Native library resource not found");
            }

            byte[] soFileContent = readFully(soFileStream);
            soFileStream.close();
            
            XposedBridge.log("[YourCheat] Read native library: " + soFileContent.length + " bytes");

            soFile.createNewFile();
            FileOutputStream out = new FileOutputStream(soFile);
            out.write(soFileContent);
            out.close();

            soFile.setExecutable(true);
            soFile.setReadable(true);
            soFile.setWritable(true);

            XposedBridge.log("[YourCheat] Extracted: " + pathname + " (" + soFileContent.length + " bytes)");

            try {
                System.load(pathname);
                XposedBridge.log("[YourCheat] Native library loaded via System.load()");
            } catch (Exception e) {
                XposedBridge.log("[YourCheat] System.load() failed: " + e.getMessage() + ", trying dlopen...");
            }

        } catch (Exception e) {
            XposedBridge.log("[YourCheat] Native library loading failed: " + e.getMessage());
            throw e;
        }
    }

    private static byte[] readFully(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    public static InputStream resourceStream(String name) {
        ClassLoader cl = Inject.class.getClassLoader();
        if (cl == null) return null;
        return cl.getResourceAsStream(name);
    }
}