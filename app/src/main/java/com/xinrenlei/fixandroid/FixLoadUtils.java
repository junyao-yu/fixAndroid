package com.xinrenlei.fixandroid;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Auth：yujunyao
 * Since: 2020/12/24 5:03 PM
 * Email：yujunyao@xinrenlei.net
 */

public class FixLoadUtils {


    public static void fixLoad(Context mContext) {

        try {
            ClassLoader pathClassLoader = mContext.getClassLoader();


            Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = baseDexClassLoader.getDeclaredField("pathList");
            pathListField.setAccessible(true);

            Object pathList = pathListField.get(pathClassLoader);


            Class<?> dexPathList = Class.forName("dalvik.system.DexPathList");
            Field dexElementsField = dexPathList.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] oldElements = (Object[]) dexElementsField.get(pathList);


            /**
             *@SuppressWarnings("unused")
             *private static Element[] makePathElements (List < File > files, File
                optimizedDirectory,
             *List<IOException> suppressedExceptions){
             *return makeDexElements(files, optimizedDirectory, suppressedExceptions, null);
             *}
             * 查看源码，反射makePathElements函数获取elements数组
             */

            Method makePathElementsMethod = dexPathList.getDeclaredMethod("makePathElements", List.class,
                    File.class, List.class);
            makePathElementsMethod.setAccessible(true);

            //参数1
            List<File> files = new ArrayList<>();
            //通过bsdiff生成补丁包
            File file = new File(mContext.getExternalCacheDir(), "patch.jar");
            files.add(file);

            //参数2
            File optimizedDirectory = mContext.getCacheDir();

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

}
