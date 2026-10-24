package com.fongmi.android.tv.player.exo;

import android.content.Context;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.trackselection.DecodeTrackSelector;

/**
 * Keeps H.264 and H.265 video on the hardware decoder when its only shortfall is the level the stream declares.
 *
 * <p>The platform renderer reports a format whose level is above the decoder's maximum as exceeding its capabilities,
 * and the FFmpeg renderer is then preferred. Some broadcasters declare a higher level than the stream needs: CCTV-4K
 * declares HEVC level 6.0 for 3840x2160 at 50 fps, which the TV's hardware decoder plays, while FFmpeg takes seconds
 * to show the first frame and cannot keep up.
 */
public class ExoTrackSelector extends DecodeTrackSelector {

    private static final String MEDIA_CODEC_VIDEO_RENDERER_NAME = "MediaCodecVideoRenderer";

    private final Context context;

    public ExoTrackSelector(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected boolean isPreferredRenderer(RendererCapabilities rendererCapability, TrackGroup group, int formatSupportLevel) {
        if (super.isPreferredRenderer(rendererCapability, group, formatSupportLevel)) return true;
        // In software mode the platform renderer is not allowed and never gets here.
        return group.type == C.TRACK_TYPE_VIDEO && formatSupportLevel == C.FORMAT_EXCEEDS_CAPABILITIES && MEDIA_CODEC_VIDEO_RENDERER_NAME.equals(rendererCapability.getName()) && exceedsOnlyLevel(group);
    }

    private boolean exceedsOnlyLevel(TrackGroup group) {
        for (int i = 0; i < group.length; i++) if (exceedsOnlyLevel(group.getFormat(i))) return true;
        return false;
    }

    private boolean exceedsOnlyLevel(Format format) {
        String mimeType = format.sampleMimeType;
        if (!MimeTypes.VIDEO_H264.equals(mimeType) && !MimeTypes.VIDEO_H265.equals(mimeType)) return false;
        try {
            for (MediaCodecInfo info : MediaCodecUtil.getDecoderInfos(mimeType, false, false)) {
                if (info.hardwareAccelerated && info.isFormatFunctionallySupported(context, format) && isSizeAndRateSupported(info, format)) return true;
            }
        } catch (MediaCodecUtil.DecoderQueryException ignored) {
        }
        return false;
    }

    private static boolean isSizeAndRateSupported(MediaCodecInfo info, Format format) {
        return format.width <= 0 || format.height <= 0 || info.isVideoSizeAndRateSupported(format.width, format.height, format.frameRate);
    }
}
