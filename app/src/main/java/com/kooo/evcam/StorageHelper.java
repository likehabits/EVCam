package com.kooo.evcam;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 存储帮助类
 * 提供外置SD卡检测和存储路径管理功能
 */
public class StorageHelper {
    private static final String TAG = "StorageHelper";
    
    // 存储目录名称
    public static final String VIDEO_DIR_NAME = "EVCam_Video";
    public static final String PHOTO_DIR_NAME = "EVCam_Photo";
    public static final String LOG_DIR_NAME = "EVCam_Log";
    
    /**
     * 检测是否有外置SD卡（并且可以写入公共目录）
     * @param context 上下文
     * @return true 如果检测到外置SD卡且可写入
     */
    public static boolean hasExternalSdCard(Context context) {
        File sdCardRoot = getExternalSdCardRoot(context);
        if (sdCardRoot == null || !sdCardRoot.exists()) {
            return false;
        }
        
        // 检查 DCIM 目录是否可写
        File dcimDir = new File(sdCardRoot, Environment.DIRECTORY_DCIM);
        if (!dcimDir.exists()) {
            // 尝试创建 DCIM 目录
            boolean created = dcimDir.mkdirs();
            if (!created) {
                AppLog.w(TAG, "无法在外置SD卡上创建 DCIM 目录");
                return false;
            }
        }
        
        return dcimDir.canWrite();
    }
    
    /**
     * 获取外置SD卡路径
     * @param context 上下文
     * @return 外置SD卡根目录，如果没有则返回 null
     */
    public static File getExternalSdCardPath(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            // 获取所有外部存储设备
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs == null || externalDirs.length < 2) {
                AppLog.d(TAG, "未检测到外置SD卡（仅有内部存储）");
                return null;
            }
            
            // 第一个是内部存储，第二个及以后是外置SD卡
            for (int i = 1; i < externalDirs.length; i++) {
                File dir = externalDirs[i];
                if (dir != null && dir.exists()) {
                    // 尝试获取SD卡根目录（去掉 /Android/data/包名/files 部分）
                    String path = dir.getAbsolutePath();
                    int index = path.indexOf("/Android/data/");
                    if (index > 0) {
                        File sdRoot = new File(path.substring(0, index));
                        if (sdRoot.exists() && sdRoot.canRead()) {
                            AppLog.d(TAG, "检测到外置SD卡: " + sdRoot.getAbsolutePath());
                            return sdRoot;
                        }
                    }
                    
                    // 如果无法获取根目录，返回应用专属目录的上级目录
                    AppLog.d(TAG, "检测到外置SD卡（应用目录）: " + dir.getAbsolutePath());
                    return dir;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "检测外置SD卡失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取外置SD卡的应用专属目录
     * @param context 上下文
     * @return 外置SD卡上的应用专属目录，如果没有则返回 null
     */
    public static File getExternalSdCardAppDir(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs != null && externalDirs.length >= 2) {
                File dir = externalDirs[1];
                if (dir != null) {
                    // 确保目录存在
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    return dir;
                }
            }
        } catch (Exception e) {
            AppLog.e(TAG, "获取外置SD卡应用目录失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取视频存储目录
     * @param context 上下文
     * @param useExternalSd 是否使用外置SD卡
     * @return 视频存储目录
     */
    public static File getVideoDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, VIDEO_DIR_NAME, Environment.DIRECTORY_DCIM);
    }
    
    /**
     * 获取图片存储目录
     * @param context 上下文
     * @param useExternalSd 是否使用外置SD卡
     * @return 图片存储目录
     */
    public static File getPhotoDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, PHOTO_DIR_NAME, Environment.DIRECTORY_DCIM);
    }
    
    /**
     * 获取日志存储目录
     * @param context 上下文
     * @param useExternalSd 是否使用外置SD卡
     * @return 日志存储目录
     */
    public static File getLogDir(Context context, boolean useExternalSd) {
        return getStorageDir(context, useExternalSd, LOG_DIR_NAME, Environment.DIRECTORY_DOWNLOADS);
    }
    
    /**
     * 根据 AppConfig 配置获取视频存储目录
     * @param context 上下文
     * @return 视频存储目录
     */
    public static File getVideoDir(Context context) {
        AppConfig config = new AppConfig(context);
        return getVideoDir(context, config.isUsingExternalSdCard());
    }
    
    /**
     * 根据 AppConfig 配置获取图片存储目录
     * @param context 上下文
     * @return 图片存储目录
     */
    public static File getPhotoDir(Context context) {
        AppConfig config = new AppConfig(context);
        return getPhotoDir(context, config.isUsingExternalSdCard());
    }
    
    /**
     * 获取存储目录
     * @param context 上下文
     * @param useExternalSd 是否使用外置SD卡
     * @param dirName 目录名称
     * @param parentDirType 父目录类型（如 DCIM, Downloads）
     * @return 存储目录
     */
    private static File getStorageDir(Context context, boolean useExternalSd, String dirName, String parentDirType) {
        File dir;
        
        if (useExternalSd) {
            // 使用外置SD卡的公共目录（SD卡/DCIM/EVCam_Video 或 SD卡/DCIM/EVCam_Photo）
            File sdCardRoot = getExternalSdCardRoot(context);
            if (sdCardRoot != null) {
                // 在SD卡的公共目录下创建子目录（如 /storage/xxxx-xxxx/DCIM/EVCam_Video）
                File parentDir = new File(sdCardRoot, parentDirType);
                dir = new File(parentDir, dirName);
            } else {
                // 如果没有外置SD卡，回退到内部存储
                AppLog.w(TAG, "外置SD卡不可用，回退到内部存储");
                dir = new File(Environment.getExternalStoragePublicDirectory(parentDirType), dirName);
            }
        } else {
            // 使用内部存储的公共目录
            dir = new File(Environment.getExternalStoragePublicDirectory(parentDirType), dirName);
        }
        
        // 确保目录存在
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                AppLog.d(TAG, "创建存储目录: " + dir.getAbsolutePath());
            } else {
                AppLog.e(TAG, "创建存储目录失败: " + dir.getAbsolutePath());
            }
        }
        
        return dir;
    }
    
    /**
     * 获取外置SD卡根目录（用于写入公共目录）
     * 简化版：优先使用用户手动设置的路径，然后只识别 XXXX-XXXX 格式
     * @param context 上下文
     * @return 外置SD卡根目录，如果没有则返回 null
     */
    public static File getExternalSdCardRoot(Context context) {
        if (context == null) {
            return null;
        }
        
        // 方法0：优先使用用户手动设置的路径
        AppConfig config = new AppConfig(context);
        String customPath = config.getCustomSdCardPath();
        if (customPath != null && !customPath.isEmpty()) {
            File customDir = new File(customPath);
            if (customDir.exists() && customDir.isDirectory() && customDir.canRead()) {
                AppLog.d(TAG, "使用自定义SD卡路径: " + customPath);
                return customDir;
            }
        }
        
        // 方法1：读取 /proc/mounts（快速可靠，能看到所有挂载的存储设备）
        File sdRoot = getSdCardFromMounts();
        if (sdRoot != null) {
            return sdRoot;
        }
        
        // 方法2：通过 getExternalFilesDirs 获取（标准 API，只接受 XXXX-XXXX 格式）
        sdRoot = getSdCardFromExternalFilesDirs(context);
        if (sdRoot != null) {
            return sdRoot;
        }
        
        // 方法3：扫描 /storage/ 目录（只识别 XXXX-XXXX 格式）
        sdRoot = getSdCardFromStorageDir();
        if (sdRoot != null) {
            return sdRoot;
        }
        
        AppLog.d(TAG, "未检测到外置SD卡");
        return null;
    }
    
    /**
     * 方法1：读取 /proc/mounts 查找 SD 卡
     * 这是最可靠的方法，能看到系统实际挂载的所有存储设备
     * 只接受 /storage/XXXX-XXXX 格式
     */
    private static File getSdCardFromMounts() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/mounts"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                
                String mountPoint = parts[1];
                // 只接受 /storage/XXXX-XXXX 格式
                if (mountPoint.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                    File sdCard = new File(mountPoint);
                    if (sdCard.exists() && sdCard.isDirectory() && sdCard.canRead()) {
                        AppLog.d(TAG, "通过 /proc/mounts 找到SD卡: " + mountPoint);
                        reader.close();
                        return sdCard;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }
    
    /**
     * 方法2：通过标准 API getExternalFilesDirs 获取 SD 卡
     * 只接受 /storage/XXXX-XXXX 格式的路径
     */
    private static File getSdCardFromExternalFilesDirs(Context context) {
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            
            if (externalDirs == null || externalDirs.length < 2) {
                return null;
            }
            
            // 第一个是内部存储，第二个及以后可能是外置SD卡
            for (int i = 1; i < externalDirs.length; i++) {
                File dir = externalDirs[i];
                if (dir != null && dir.exists()) {
                    String path = dir.getAbsolutePath();
                    int index = path.indexOf("/Android/data/");
                    if (index > 0) {
                        String sdRootPath = path.substring(0, index);
                        // 只接受 /storage/XXXX-XXXX 格式
                        if (sdRootPath.matches("/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                            File sdRoot = new File(sdRootPath);
                            if (sdRoot.exists() && sdRoot.canRead()) {
                                AppLog.d(TAG, "通过 getExternalFilesDirs 找到SD卡: " + sdRoot.getAbsolutePath());
                                return sdRoot;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }
    
    /**
     * 方法3：扫描 /storage/ 目录查找 SD 卡
     * 简化版：只扫描一次，只识别 XXXX-XXXX 格式
     */
    private static File getSdCardFromStorageDir() {
        try {
            File storageDir = new File("/storage");
            File[] files = storageDir.listFiles();
            if (files == null || files.length == 0) {
                return null;
            }
            
            for (File file : files) {
                String name = file.getName();
                // 只识别 XXXX-XXXX 格式（典型的 SD 卡命名）
                if (name.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
                    if (file.isDirectory() && file.canRead()) {
                        AppLog.d(TAG, "通过扫描 /storage/ 找到SD卡: " + file.getAbsolutePath());
                        return file;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误，继续其他方法
        }
        return null;
    }
    
    
    /**
     * 获取所有检测到的存储设备信息（用于调试）
     * @param context 上下文
     * @return 存储设备信息列表
     */
    public static List<String> getStorageDebugInfo(Context context) {
        List<String> info = new ArrayList<>();
        
        // 0. 显示内部存储路径（用于对比）
        info.add("=== 内部存储 ===");
        String internalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        info.add("路径: " + internalPath);
        info.add("");
        
        // 1. getExternalFilesDirs 信息
        info.add("=== getExternalFilesDirs ===");
        try {
            File[] externalDirs = context.getExternalFilesDirs(null);
            if (externalDirs != null) {
                for (int i = 0; i < externalDirs.length; i++) {
                    File dir = externalDirs[i];
                    if (dir != null) {
                        String label = (i == 0) ? "[0] 内部" : "[" + i + "] 外部";
                        info.add(label + ": " + dir.getAbsolutePath());
                        info.add("    exists=" + dir.exists() + ", canWrite=" + dir.canWrite());
                    } else {
                        info.add("[" + i + "] null");
                    }
                }
            } else {
                info.add("返回 null");
            }
        } catch (Exception e) {
            info.add("错误: " + e.getMessage());
        }
        
        // 2. /storage/ 目录内容
        info.add("");
        info.add("=== /storage/ 目录 ===");
        try {
            File storageDir = new File("/storage");
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    String marker = "";
                    // 标记内部存储相关目录
                    if (name.equals("emulated") || name.equals("self") || name.equals("sdcard0")) {
                        marker = " [内部]";
                    } else if (name.matches("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")) {
                        marker = " [可能是SD卡]";
                    }
                    info.add(name + marker + " (dir=" + file.isDirectory() + 
                            ", read=" + file.canRead() + ", write=" + file.canWrite() + ")");
                    
                    // 检查是否有 DCIM 目录
                    if (file.isDirectory()) {
                        File dcim = new File(file, "DCIM");
                        if (dcim.exists()) {
                            info.add("    └─ 有 DCIM 目录");
                        }
                    }
                }
            } else {
                info.add("无法列出目录内容（可能需要权限）");
            }
        } catch (Exception e) {
            info.add("错误: " + e.getMessage());
        }
        
        // 3. /mnt/ 目录内容
        info.add("");
        info.add("=== /mnt/ 目录 ===");
        try {
            File mntDir = new File("/mnt");
            File[] files = mntDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    String marker = "";
                    if (name.contains("sd") || name.contains("SD") || 
                        name.contains("usb") || name.contains("USB") ||
                        name.contains("external") || name.contains("tf") || name.contains("TF")) {
                        marker = " [可能是外部存储]";
                    }
                    info.add(name + marker + " (dir=" + file.isDirectory() + 
                            ", read=" + file.canRead() + ", write=" + file.canWrite() + ")");
                }
            } else {
                info.add("无法列出目录内容（可能需要权限）");
            }
        } catch (Exception e) {
            info.add("错误: " + e.getMessage());
        }
        
        // 4. 检测结果
        info.add("");
        info.add("=== 检测结果 ===");
        File sdCard = getExternalSdCardRoot(context);
        if (sdCard != null) {
            info.add("检测到SD卡: " + sdCard.getAbsolutePath());
            info.add("可写入: " + sdCard.canWrite());
            
            // 检查是否与内部存储相同（错误检测）
            if (sdCard.getAbsolutePath().equals(internalPath) ||
                sdCard.getAbsolutePath().startsWith(internalPath + "/") ||
                internalPath.startsWith(sdCard.getAbsolutePath() + "/")) {
                info.add("⚠️ 警告: 检测的路径可能与内部存储重叠！");
            }
        } else {
            info.add("未检测到SD卡");
        }
        
        return info;
    }
    
    /**
     * 获取存储空间信息
     * @param path 存储路径
     * @return 可用空间（字节），如果获取失败返回 -1
     */
    public static long getAvailableSpace(File path) {
        if (path == null || !path.exists()) {
            return -1;
        }
        
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            AppLog.e(TAG, "获取存储空间信息失败", e);
            return -1;
        }
    }
    
    /**
     * 获取总存储空间
     * @param path 存储路径
     * @return 总空间（字节），如果获取失败返回 -1
     */
    public static long getTotalSpace(File path) {
        if (path == null || !path.exists()) {
            return -1;
        }
        
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            AppLog.e(TAG, "获取总存储空间失败", e);
            return -1;
        }
    }
    
    /**
     * 格式化存储大小显示
     * @param bytes 字节数
     * @return 格式化后的字符串（如 "1.5 GB"）
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        
        final long KB = 1024;
        final long MB = KB * 1024;
        final long GB = MB * 1024;
        
        if (bytes >= GB) {
            return String.format("%.1f GB", (double) bytes / GB);
        } else if (bytes >= MB) {
            return String.format("%.1f MB", (double) bytes / MB);
        } else if (bytes >= KB) {
            return String.format("%.1f KB", (double) bytes / KB);
        } else {
            return bytes + " B";
        }
    }
    
    /**
     * 获取存储信息描述
     * @param context 上下文
     * @param useExternalSd 是否使用外置SD卡
     * @return 存储信息描述字符串
     */
    public static String getStorageInfoDesc(Context context, boolean useExternalSd) {
        File storageDir;
        String storageName;
        
        if (useExternalSd) {
            storageDir = getExternalSdCardRoot(context);
            storageName = "外置SD卡";
            if (storageDir == null) {
                return "外置SD卡不可用";
            }
        } else {
            storageDir = Environment.getExternalStorageDirectory();
            storageName = "内部存储";
        }
        
        long available = getAvailableSpace(storageDir);
        long total = getTotalSpace(storageDir);
        
        if (available < 0 || total < 0) {
            return storageName;
        }
        
        return String.format("%s（可用 %s / 共 %s）", 
                storageName, 
                formatSize(available), 
                formatSize(total));
    }
    
    /**
     * 获取当前存储路径描述
     * @param context 上下文
     * @return 当前存储路径描述
     */
    public static String getCurrentStoragePathDesc(Context context) {
        AppConfig config = new AppConfig(context);
        boolean useExternalSd = config.isUsingExternalSdCard();
        
        File videoDir = getVideoDir(context, useExternalSd);
        return videoDir.getAbsolutePath();
    }
}
