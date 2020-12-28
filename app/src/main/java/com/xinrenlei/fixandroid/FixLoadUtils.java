package com.xinrenlei.fixandroid;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Auth：yujunyao
 * Since: 2020/12/24 5:03 PM
 * Email：yujunyao@xinrenlei.net
 */

public class FixLoadUtils {

    /**
     * 测试的话可以  dx --dex --output=patch.jar com/xinrenlei/fixandroid/Utils.class  可以通过 dx文件生成dex文件
     */
    public static void fixLoad(Application application) {

        File hackFile = initHack(application);
        //参数1
        List<File> files = new ArrayList<>();
        files.add(hackFile);
        //通过dx生成补丁包
        File file = new File(application.getExternalCacheDir(), "patch.jar");
        files.add(file);


        ClassLoader classLoader = application.getClassLoader();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
//                inject(application, classLoader, files);
                ClassLoaderInjector.inject(application, classLoader, files);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return;
        }

        try {
//            ClassLoader pathClassLoader = application.getClassLoader();


//            Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = findField(classLoader, "pathList");
//            pathListField.setAccessible(true);
            Object pathList = pathListField.get(classLoader);


//            Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
            Field dexElementsField = findField(pathList, "dexElements");
//            dexElementsField.setAccessible(true);
            Object[] oldElements = (Object[]) dexElementsField.get(pathList);


            /**
             *@SuppressWarnings("unused")
             *private static Element[] makePathElements (List < File > files, File
                optimizedDirectory,
             *List<IOException> suppressedExceptions){
             *return makeDexElements(files, optimizedDirectory, suppressedExceptions, null);
             *}
             * 查看源码，反射反射makePathElements函数获取elements数组函数获取elements数组
             *
             * 不同的api，makePathElements参数或者函数名可能都有所不同，所以正规是要做兼容的
             */

            Method makePathElementsMethod = findMethod(pathList, "makePathElements", List.class,
                    File.class, List.class);
//            makePathElementsMethod.setAccessible(true);


            //参数2
            File optimizedDirectory = application.getCacheDir();

            //参数3
            ArrayList<IOException> suppressedExceptions = new ArrayList<>();

            Object[] makePathElements = (Object[]) makePathElementsMethod.invoke(pathList, files, optimizedDirectory, suppressedExceptions);

            //补丁包的elements在前
            Object[] newElements = (Object[]) Array.newInstance(oldElements.getClass().getComponentType(), oldElements.length + makePathElements.length);
            System.arraycopy(makePathElements, 0, newElements, 0, makePathElements.length);
            System.arraycopy(oldElements, 0, newElements, makePathElements.length, oldElements.length);

            dexElementsField.set(pathList, newElements);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    /**
     * =====
     * 不同的api，makePathElements参数或者函数名可能都有所不同，所以正规是要做兼容的
     * =====
     */


    /**
     * ======
     * Android N 以后，ART模式采用混合模式运行，应用在安装时不做编译，而是运行时解释执行，通时在JIT编译一些代码后保存到profile文件，等
     * 设备空闲的时候使用AOT编译生成app_image的base.art文件，这个art文件会在apk启动时自动加载。这步操作，在我们修复补丁之前
     * 解决方案是：运行时替换PathClassLoader方案
     * ======
     */
//    public static void inject(Application app, ClassLoader oldClassLoader, List<File> patchs) throws Throwable {
//
//        //创建自己的加载器
//        ClassLoader newClassLoader = createNewClassLoader(app, oldClassLoader, patchs);
//        classLoaderInject(app, newClassLoader);
//    }
//
//    public static ClassLoader createNewClassLoader(Context context, ClassLoader oldClassLoader, List<File> patchs) throws Throwable {
//        //1、把补丁包的dex拼起来--获取原始的dexPath，用于构造ClassLoader
//        StringBuilder dexPathBuilder = new StringBuilder();
//        String packageName = context.getPackageName();
//        boolean isFirst = true;
//        for (File patch : patchs) {
//            if (isFirst) {
//                isFirst = false;
//            } else {
//                dexPathBuilder.append(File.pathSeparator);
//            }
//
//            dexPathBuilder.append(patch.getAbsolutePath());
//        }
//        Log.e("dexPathBuilder--->", dexPathBuilder.toString());
//
//        //2、把Apk中的dex拼起来
//        Field pathListField = findField(oldClassLoader, "pathList");
//        Object oldPathList = pathListField.get(oldClassLoader);
//
//        Field dexElementsField = findField(oldPathList, "dexElements");
//        Object[] oldDexElements = (Object[]) dexElementsField.get(oldPathList);
//
//        //从element上面得到dexFile
//        Field dexFileField = findField(oldDexElements[0], "dexFile");
//        for (Object oldDexElement : oldDexElements) {
//            String dexPath = null;
//            DexFile dexFile = (DexFile) dexFileField.get(oldDexElement);
//            if (dexFile != null) {
//                dexPath = dexFile.getName();
//                Log.e("dexPath--->", dexPath);
//            }
//            if (dexPath == null || dexPath.isEmpty()) {
//                continue;
//            }
//            if (dexPath.contains("/" + packageName)) {
//                continue;
//            }
//            if (isFirst) {
//                isFirst = false;
//            } else {
//                dexPathBuilder.append(File.pathSeparator);
//            }
//            dexPathBuilder.append(dexPath);
//        }
//        String combineDexPath = dexPathBuilder.toString();
//        Log.e("combineDexPath--->", combineDexPath);
//
//        //3、获取apk中so的加载路径
//        Field nativeLibraryDirectoriesField = findField(oldPathList, "nativeLibraryDirectories");
//        List<File> oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);
//        StringBuilder libraryPathBuilder = new StringBuilder();
//        isFirst = true;
//        for (File file : oldNativeLibraryDirectories) {
//            if (file == null) {
//                continue;
//            }
//            if (isFirst) {
//                isFirst = false;
//            } else {
//                libraryPathBuilder.append(File.pathSeparator);
//            }
//            libraryPathBuilder.append(file.getAbsolutePath());
//        }
//        String combineLibraryPath = libraryPathBuilder.toString();
//
//        Log.e("combineLibraryPath--->", combineLibraryPath);
//
//        return new CustomClassLoader(combineDexPath, combineLibraryPath, ClassLoader.getSystemClassLoader());
//    }
//
//    //替换用到的classLoader
//    private static void classLoaderInject(Application app, ClassLoader classLoader) throws Throwable {
//        Thread.currentThread().setContextClassLoader(classLoader);
//
//        Context baseContext = (Context) findField(app, "mBase").get(app);
//        if (Build.VERSION.SDK_INT >= 26) {
//            findField(baseContext, "mClassLoader").set(baseContext, classLoader);
//        }
//
//        Object basePackageInfo = findField(baseContext, "mPackageInfo").get(baseContext);
//        findField(basePackageInfo, "mClassLoader").set(basePackageInfo, classLoader);
//
//        if (Build.VERSION.SDK_INT < 27) {
//            Resources resources = app.getResources();
//
//            findField(resources, "mClassLoader").set(resources, classLoader);
//
//            Object mDrawableInflater = findField(resources, "mDrawableInflater").get(resources);
//            if (mDrawableInflater != null) {
//                findField(mDrawableInflater, "mClassLoader").set(mDrawableInflater, classLoader);
//            }
//        }
//    }
//
//
    /**
     * 从 instance 到其父类 找 name 属性
     *
     * @param instance
     * @param name
     * @return
     * @throws NoSuchFieldException
     */
    public static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                //查找当前类的 属性(不包括父类)
                Field field = clazz.getDeclaredField(name);

                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                return field;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * 从 instance 到其父类 找  name 方法
     *
     * @param instance
     * @param name
     * @return
     * @throws NoSuchFieldException
     */
    public static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);

                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }

                return method;
            } catch (NoSuchMethodException e) {
                // ignore and search next
            }
        }
        throw new NoSuchMethodException("Method "
                + name
                + " with parameters "
                + Arrays.asList(parameterTypes)
                + " not found in " + instance.getClass());
    }

    /**
     * ====== 在Dalvik虚拟机安装期间，会出现这样的一种情况，
     * Java类的所有引用是否与自己处于同一个dex中，如果是会被打入标签CLASS_ISPREVERIFIED，这样如果引用了别的dex文件会被校验失败，报错！！！
     * 解决方案是，比如我们有个备用的hack.dex包文件，让每个类都引用hack.dex文件中的一个类(这个要用到字节码插桩)，破除效验标签CLASS_ISPREVERIFIED
     * ======
     */
    private static File initHack(Context context) {
        File hackFile = new File(context.getExternalCacheDir(), "hack.dex");
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            fos = new FileOutputStream(hackFile);
            is  = context.getAssets().open("hack.dex");
            int len;
            byte[] buffer = new byte[2048];
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return hackFile;
    }


}
