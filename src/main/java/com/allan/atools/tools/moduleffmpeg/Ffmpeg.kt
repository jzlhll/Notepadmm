package com.allan.atools.tools.moduleffmpeg

import com.allan.atools.utils.IO
import com.allan.atools.utils.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.Throws


class Ffmpeg(private val settings:FfmpegSettings) {
    //other：ffmpeg -y -loglevel error  -i "$1" -movflags faststart "$1-fast.mp4"

    //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i orig.mp4 -c:v libx265 -x265-params crf=24:preset=placebo new3.mp4
    //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i orig.mp4 -ss 00:00:01 -t 5 -f image2 -r 2 ls/pic03-%03d.jpg
    //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i cover.jpg -i smallCut.mp4 -map 1:0 -map 1:1 -map 0:0 -c copy -disposition:2 attached_pic smallCut2.mp4

    private fun ffmpegBin() = if(settings.ffmpegBinDir.isEmpty()) throw NoFfmpegBinException() else IO.combinePath(settings.ffmpegBinDir, "ffmpeg.exe").also { if(!isExist(it)) throw NoFfmpegBinException() }
    private fun ffprobeBin() = if(settings.ffmpegBinDir.isEmpty()) throw NoFfmpegBinException() else IO.combinePath(settings.ffmpegBinDir, "ffprobe.exe").also { if(!isExist(it)) throw NoFfmpegBinException() }

    fun isExist(filePath:String) = File(filePath).exists()

    @Throws(NoFfmpegBinException::class)
    fun showVideoInfo(file:File) {
        val cmd = ffprobeBin() + " -i ${file.absolutePath}"
        Log.d("showVideoInfo: $cmd")
        IO.runBig(cmd)
    }

    private fun formatSecondsToTime(seconds: Int): String {
        val hour = seconds / 3600
        val minute = (seconds % 3600) / 60
        val second = seconds % 60
        return String.format("%02d:%02d:%02d", hour, minute, second)
    }

    @Throws(NoFfmpegBinException::class)
    fun combine(videoFile:File, imageFile:File) {
        //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i cover.jpg -i smallCut.mp4 -map 1:0 -map 1:1 -map 0:0 -c copy -disposition:2 attached_pic smallCut2.mp4

        var index = 1
        val fileName = videoFile.name
        var newFileNam:String? = null
        while (true) {
            val f = File(IO.combinePath(settings.workspaceDir, "combine_${index}_$fileName"))
            if (!f.exists()) {
                newFileNam = f.absolutePath
                break
            }
            index++
        }

        val cmd = ffmpegBin() + " -i ${imageFile.absolutePath} -i ${videoFile.absolutePath} -map 1:0 -map 1:1 -map 0:0 -c copy -disposition:2 attached_pic $newFileNam"
        Log.d("combineCover: $cmd")
        IO.runBig(cmd, true)
    }

    @Throws(NoFfmpegBinException::class)
    fun generateCovers(videoFile:File, start:Int, total:Int, perSecond:Int) {
        //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i orig.mp4 -ss 00:00:01 -t 5 -f image2 -r 2 ls/pic03-%03d.jpg
        val startStr = formatSecondsToTime(start)
        val workDir = IO.combinePathWithInclineEnd(settings.workspaceDir)
        val cmd = ffmpegBin() + " -i ${videoFile.absolutePath} -ss $startStr -t $total -f image2 -r $perSecond ${workDir}cover_%04d.jpg"
        Log.d("generateCovers: $cmd")
        IO.runBig(cmd, true)
    }

    fun deleteCoverFiles() {
        val directory = File(settings.workspaceDir)
        if (!directory.isDirectory) {
            return
        }
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("cover_")) {
                file.delete()
            }
        }
    }

    @Throws(NoFfmpegBinException::class)
    fun compressVideo(videoFile:File, crf:Int, speed:String) {
        //D:\profiles\ffmpeg-6.0-full_build\bin\ffmpeg.exe -i orig.mp4 -c:v libx265 -x265-params crf=24:preset=placebo new3.mp4   -》 9M，缺失封面
        val fileName = videoFile.name
        var index = 1
        var newFileNam:String? = null
        while (true) {
            val f = File(IO.combinePath(settings.workspaceDir, "compress_${index}_$fileName"))
            if (!f.exists()) {
                newFileNam = f.absolutePath
                break
            }
            index++
        }

        val cmd = ffmpegBin() + " -i ${videoFile.absolutePath} -c:v libx265 -x265-params crf=${crf}:preset=${speed} $newFileNam"
        Log.d("compressVideo: $cmd")
        IO.runBig(cmd)
    }
}

class NoFfmpegBinException:RuntimeException()