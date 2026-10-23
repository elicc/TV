package com.fongmi.android.tv.api.parser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The channels and programmes of an XMLTV file, read with a pull parser. The reflection based
 * reader took tens of seconds for a file of 10,000 programmes on a TV.
 */
final class EpgXml {

    static final class Channel {

        final String id;
        final List<String> names = new ArrayList<>();
        String src = "";

        Channel(String id) {
            this.id = id;
        }
    }

    static final class Programme {

        final String start;
        final String stop;
        final String channel;
        final String title;

        Programme(String start, String stop, String channel, String title) {
            this.start = start;
            this.stop = stop;
            this.channel = channel;
            this.title = title;
        }
    }

    final Map<String, List<Channel>> channels = new HashMap<>();
    final List<Programme> programmes = new ArrayList<>();

    static EpgXml parse(XmlPullParser parser, InputStream input) throws IOException, XmlPullParserException {
        parser.setInput(input, null);
        EpgXml xml = new EpgXml();
        Channel channel = null;
        boolean inProgramme = false;
        String start = "", stop = "", programmeChannel = "", title = "";
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                switch (parser.getName()) {
                    case "channel" -> {
                        channel = new Channel(attribute(parser, "id"));
                        xml.channels.computeIfAbsent(channel.id, id -> new ArrayList<>()).add(channel);
                    }
                    case "display-name" -> {
                        String name = parser.nextText().trim();
                        if (channel != null && !name.isEmpty()) channel.names.add(name);
                    }
                    case "icon" -> {
                        if (channel != null && channel.src.isEmpty()) channel.src = attribute(parser, "src");
                    }
                    case "programme" -> {
                        inProgramme = true;
                        start = attribute(parser, "start");
                        stop = attribute(parser, "stop");
                        programmeChannel = attribute(parser, "channel");
                        title = "";
                    }
                    case "title" -> {
                        String text = parser.nextText().trim();
                        if (inProgramme && title.isEmpty()) title = text;
                    }
                    default -> {
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                switch (parser.getName()) {
                    case "channel" -> channel = null;
                    case "programme" -> {
                        xml.programmes.add(new Programme(start, stop, programmeChannel, title));
                        inProgramme = false;
                    }
                    default -> {
                    }
                }
            }
        }
        return xml;
    }

    private static String attribute(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? "" : value;
    }
}
