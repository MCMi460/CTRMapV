package ctrmap.util.tools;

import ctrmap.editor.gui.editors.gen5.text.VTextLoader;
import ctrmap.formats.pokemon.text.GenVMessageHandler;
import ctrmap.formats.pokemon.text.ITextFile;
import ctrmap.formats.pokemon.text.MsgStr;
import ctrmap.formats.pokemon.text.TextFile;
import ctrmap.formats.pokemon.text.TextFileFriendlizer;
import ctrmap.formats.pokemon.text.TextFileRW;
import xstandard.cli.ArgumentBuilder;
import xstandard.cli.ArgumentContent;
import xstandard.cli.ArgumentPattern;
import xstandard.cli.ArgumentType;
import xstandard.fs.FSUtil;
import xstandard.fs.FSFile;
import xstandard.fs.accessors.DiskFile;
import xstandard.gui.file.ExtensionFilter;
import xstandard.gui.file.CommonExtensionFilters;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class TextUtil {
    public static class Extensions {
        public static final ExtensionFilter TEXT_BINARY_FILTER = new ExtensionFilter("TextFile raw binary", "*.bin");
        public static final ExtensionFilter TEXT_PLAIN_FILTER = CommonExtensionFilters.PLAIN_TEXT;
    }
    public static enum Types {
        BINARY,
        TEXT
    }
    public static class FakeFile extends DiskFile {
        public FakeFile() {
            super("");
        }
        
        @Override
        public boolean exists() {
            return false;
        }
    }

	private static final ArgumentPattern[] CLI_ARGS = new ArgumentPattern[]{
		new ArgumentPattern("type", "File type for final output (bin/txt)", ArgumentType.STRING, null, false, "-t", "--type"),
		new ArgumentPattern("inputs", "Input textfile(s) to parse", ArgumentType.STRING, null, true, "-i", "--input"),
        new ArgumentPattern("output", "Optional output file specification (allowed for single input only)", ArgumentType.STRING, null, false, "-o", "--output"),
        new ArgumentPattern("arctype", "Arc type for input(s) (system/script)", ArgumentType.STRING, VTextLoader.ArcType.SCRIPT_MSG.getManagerPackageName(), false, "-a", "--arctype"),
        new ArgumentPattern("help", "Prints this help dialog", ArgumentType.BOOLEAN, false, "-h", "-?", "--help")
    };

    public static void main(String[] args) {
		ArgumentBuilder bld = new ArgumentBuilder(CLI_ARGS);
		bld.parse(args);
		ArgumentContent inputPaths = bld.getContent("inputs");
		if (inputPaths.contents.isEmpty() && bld.defaultContent.contents.isEmpty()) {
			System.out.println("No inputs provided\n");
			bld.print();
		} else {
			if (bld.getContent("help").booleanValue()) {
				bld.print();
			}

            List<FSFile> inputs = new ArrayList<>();
			for (int i = 0; i < inputPaths.contents.size(); i++) {
				inputs.add(new DiskFile(inputPaths.stringValue(i)));
			}
			for (int i = 0; i < bld.defaultContent.contents.size(); i++) {
				inputs.add(new DiskFile(bld.defaultContent.stringValue(i)));
			}

            ArgumentContent output = bld.getContent("output", true);
            FSFile outputFile = null;
            if (output != null) {
                if (inputs.size() > 1) {
                    throw new UnsupportedOperationException("Optional output file can be specified a single input file only");
                }
                outputFile = new DiskFile(output.stringValue());
            }

            VTextLoader.ArcType arcType = null;
            ArgumentContent arcTypeSpec = bld.getContent("arctype");
            for (VTextLoader.ArcType typeV : VTextLoader.ArcType.values()) {
                if (typeV.getManagerPackageName().equals(arcTypeSpec.stringValue())) {
                    arcType = typeV;
                }
            }
            if (arcType == null) {
                throw new UnsupportedOperationException("Unknown arc type: " + arcTypeSpec.stringValue());
            } else if (arcType.equals(VTextLoader.ArcType.PROFANITY)) {
                throw new UnsupportedOperationException("Profanity files are unsupported currently");
            }

            ExtensionFilter outExt;
            ArgumentContent outArg = bld.getContent("type");
            Types outType = null;
            if (outArg.stringValue().equals("bin")) {
                outExt = Extensions.TEXT_BINARY_FILTER;
                outType = Types.BINARY;
            } else if (outArg.stringValue().equals("txt")) {
                outExt = Extensions.TEXT_PLAIN_FILTER;
                outType = Types.TEXT;
            } else {
                throw new UnsupportedOperationException("Unknown output file type: " + outType.name());
            }

			try {
				for (FSFile in : inputs) {
					if (!in.exists() || in.isDirectory()) {
                        System.out.println("Could not read file " + in);
						continue;
					}

                    String inExt = FSUtil.getFileExtension(in.getName());
                    ITextFile textFile;
                    if (inExt.equals(Extensions.TEXT_BINARY_FILTER.getPrimaryExtension().substring(1))) {
                        textFile = new TextFile(in, GenVMessageHandler.INSTANCE);
                    } else if (inExt.equals(Extensions.TEXT_PLAIN_FILTER.getPrimaryExtension().substring(1))) {
                        if (arcType.equals(VTextLoader.ArcType.PROFANITY)) {
                            // GFProfanityCheck not implemented
                            textFile = null;
                        } else {
                            textFile = new TextFile(new FakeFile(), GenVMessageHandler.INSTANCE);
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(in.getNativeInputStream(), StandardCharsets.UTF_8));
                            String line;
                            ArrayList<String> lines = new ArrayList<String>();
                            while ((line = reader.readLine()) != null) {
                                lines.add(line);
                            }
                            ListIterator<String> iter = lines.listIterator();
                            while (iter.hasNext()) {
                                textFile.insertFriendlyLine(iter.nextIndex(), iter.next());
                            }
                            TextFile textProper = (TextFile)textFile;
                            textProper.enableEncryption = true;
                        }
                    } else {
                        System.out.println("Unknown file type " + inExt + " of file: " + in);
						continue;
                    }
                    
					FSFile out;
					if (outputFile != null) {
						out = outputFile;
					} else {
						out = in.getParent().getChild(FSUtil.getFileNameWithoutExtension(in.getName()) + outExt.getPrimaryExtension());
					}
                    out.touch();

                    if (outType == Types.BINARY) {
                        if (arcType.equals(VTextLoader.ArcType.PROFANITY)) {
                            // GFProfanityCheck not implemented
                        } else {
                            out.setBytes(TextFileRW.getBytesForFile((TextFile)textFile, GenVMessageHandler.INSTANCE));
                        }
                    } else {
                        List<MsgStr> lines = textFile.getLines();
                        String[] strings = new String[lines.size()];
                        for (int i = 0; i < strings.length; i++) {
                            strings[i] = TextFileFriendlizer.getFriendlized(lines.get(i).value);
                        }
                        out.setBytes(String.join("\n", strings).getBytes(StandardCharsets.UTF_8));
                    }
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
        }
    }
}
