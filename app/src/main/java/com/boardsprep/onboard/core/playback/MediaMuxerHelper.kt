package com.boardsprep.onboard.core.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Native Android zero-dependency media muxer.
 * Merges video (mp4) and audio (m4a/aac) tracks into a single MP4 container
 * using standard Android OS platform APIs (android.media.MediaMuxer & MediaExtractor).
 * Requires 0 KB of native binaries or FFmpeg!
 */
object MediaMuxerHelper {

    private const val TAG = "MediaMuxerHelper"

    fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "Video file is missing or empty: ${videoFile.absolutePath}")
            return false
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "Audio file is missing or empty: ${audioFile.absolutePath}")
            return false
        }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 1. Setup video track
            var videoTrackIndex = -1
            var muxerVideoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    Log.d(TAG, "Selected video track $i: mime=$mime, format=$format")
                    muxerVideoTrackIndex = muxer.addTrack(format)
                    videoExtractor.selectTrack(i)
                    break
                }
            }

            // 2. Setup audio track
            var audioTrackIndex = -1
            var muxerAudioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    Log.d(TAG, "Selected audio track $i: mime=$mime, format=$format")
                    muxerAudioTrackIndex = muxer.addTrack(format)
                    audioExtractor.selectTrack(i)
                    break
                }
            }

            if (muxerVideoTrackIndex < 0 || muxerAudioTrackIndex < 0) {
                Log.e(TAG, "Could not find valid video or audio track. VideoIdx: $muxerVideoTrackIndex, AudioIdx: $muxerAudioTrackIndex")
                return false
            }

            muxer.start()

            val bufferSize = 1024 * 1024 // 1 MB buffer
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var hasVideo = true
            var hasAudio = true

            // Interleave video and audio samples strictly by presentation timestamp
            while (hasVideo || hasAudio) {
                val videoTime = if (hasVideo) videoExtractor.sampleTime else Long.MAX_VALUE
                val audioTime = if (hasAudio) audioExtractor.sampleTime else Long.MAX_VALUE

                if (hasVideo && (!hasAudio || videoTime <= audioTime)) {
                    bufferInfo.offset = 0
                    bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        hasVideo = false
                    } else {
                        bufferInfo.presentationTimeUs = videoTime
                        bufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrackIndex, buffer, bufferInfo)
                        videoExtractor.advance()
                    }
                } else if (hasAudio) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        hasAudio = false
                    } else {
                        bufferInfo.presentationTimeUs = audioTime
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                        audioExtractor.advance()
                    }
                }
            }

            muxer.stop()
            muxer.release()
            muxer = null

            return outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed: ${e.message}", e)
            return false
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
