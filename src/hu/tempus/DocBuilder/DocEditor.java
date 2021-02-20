package hu.tempus.DocBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import hu.tempus.DocBuilder.DocEditor.TemplateFilter.Chunk;
import hu.tempus.HtmlGui.IOUtils;
import hu.tempus.HtmlGui.JsonHelper;

public class DocEditor {

	protected static final AtomicInteger LAST_FILE_ID = new AtomicInteger(0);
	protected static final ConcurrentMap<Integer, DocEditor> FILES = new ConcurrentHashMap<>();

	protected static final Map<Integer, TemplateFilter> TEMPLATES = new HashMap<>();

	protected final Integer fileId;
	protected final Template tpl;
	protected List<Chunk> chunks;
	protected static String error = "";

	protected DocEditor(Integer fileId, Template tpl) {
		this.fileId = fileId;
		this.tpl = tpl;
	}

	public static void addTemplate(File file) {
		TemplateFilter filter = new TemplateFilter(file);
		TEMPLATES.put(TEMPLATES.size() + 1, filter);
	}

	public static DocEditor load(Integer fileId) {
		return FILES.get(fileId);
	}

	public String getLastError() {
		return error;
	}

	public List<Chunk> getChunks() {
		return chunks;
	}

	public void setChunks(JsonArray chunks) {
		Iterator<Chunk> cit = this.chunks.iterator();
		Iterator<JsonValue> uit = chunks.iterator();
		if (!cit.hasNext()) {
			return;
		}
		Chunk c = cit.next();
		while (uit.hasNext()) {
			JsonObject u = (JsonObject) uit.next();
			int uid = u.getInt("id", 0);
			while (c.id == null || c.id < uid) {
				if (!cit.hasNext()) {
					return;
				}
				c = cit.next();
			}
			if (c.id > uid) {
				continue;
			}
			c.value = (u.getBoolean("append", false) ? c.value : "") + u.getString("value");
		}
	}

	public static DocEditor open() {
		Template tpl = choose(null, "Create/Open a file", "Open");
		if (tpl == null) {
			return null;
		}
		String path = tpl.file.getAbsolutePath();
		for (Map.Entry<Integer, DocEditor> f : FILES.entrySet()) {
			if (path.equals(f.getValue().tpl.file.getAbsolutePath())) {
				return f.getValue();
			}
		}
		DocEditor editor = new DocEditor(LAST_FILE_ID.incrementAndGet(), tpl);
		if (!editor.parseFile(tpl)) {
			return editor;
		}
		FILES.put(editor.fileId, editor);
		return editor;
	}

	public boolean save(boolean create) {
		error = "";
		if (create) {
			Template tpl = choose(this.tpl, "Save the file", "Save");
			if (tpl == null)
				return false;
		}
		return buildFile(tpl.file);
	}

	protected static Template choose(Template current, String title, String button) {
		try {
			JFrame frame = new JFrame();
			frame.setAlwaysOnTop(true);

			JFileChooser chooser = new JFileChooser();
			if (current == null) {
				chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
			} else {
				chooser.setSelectedFile(current.file);
			}
			chooser.setDialogTitle(title);

			for (TemplateFilter tpl : TEMPLATES.values()) {
				chooser.addChoosableFileFilter(tpl);
			}
			if (TEMPLATES.size() > 0) {
				chooser.setFileFilter(current != null ? current.filter : TEMPLATES.get(1));
				chooser.setAcceptAllFileFilterUsed(false);
			}

			int result = chooser.showDialog(frame, button);
			if (result == JFileChooser.APPROVE_OPTION) {
				Template tpl = new Template();
				tpl.filter = (TemplateFilter) chooser.getFileFilter();
				tpl.file = chooser.getSelectedFile();
				return tpl;
			}
		} catch (Exception e) {
			error = e.getMessage();
			e.printStackTrace(System.err);
		}
		return null;
	}

	protected boolean parseFile(Template tpl) {
		try {
			String content;
			if (!tpl.file.exists()) {
				content = tpl.filter.getDefaultContent();
				if (content == null) {
					error = "This template does not support creating files";
					return false;
				}
			} else {
				content = IOUtils.read(new FileInputStream(tpl.file));
			}
			chunks = tpl.filter.getChunks(content);
			return true;
		} catch (Exception e) {
			error = e.getMessage();
			e.printStackTrace(System.err);
		}
		return false;
	}

	public boolean buildFile(File file) {
		try (OutputStream os = new FileOutputStream(file, false)) {
			for (Chunk chunk : chunks) {
				os.write(chunk.value.getBytes("UTF-8"));
			}
			return true;
		} catch (Exception e) {
			error = e.getMessage();
			e.printStackTrace(System.err);
		}
		return false;
	}

	protected static class Template {
		public TemplateFilter filter;
		public File file;
	}

	protected static class TemplateFilter extends FileFilter {
		protected final JsonObject config;
		protected final String[] extension;
		protected final FileFilter extFilter;
		protected final File defaultContent;
		protected final Map<String, String> splitter;

		public TemplateFilter(File file) {
			config = (JsonObject) JsonHelper.parse(new IOUtils.ReadFile(file));
			extension = config.getString("extension", "").split(", *");
			extFilter = new FileNameExtensionFilter(config.getString("name", file.getName().replace("\\.[^.]+$", "")),
					extension);
			String cf = config.getString("template", "");
			defaultContent = cf.isEmpty() ? null : new File(cf);
			splitter = new LinkedHashMap<>();
			config.getJsonObject("chunks").forEach((name, value) -> splitter.put(name, ((JsonString) value).getString()));
		}

		public String getExtension(boolean with_dot) {
			String ext = extension[0];
			return !ext.isEmpty() ? "." + ext : "";
		}

		public class Chunk {
			public Integer id;
			public String name;
			public String value;
		}

		public List<Chunk> getChunks(String content) {
			Map<Integer[], Chunk> matches = new TreeMap<>(
					(a, b) -> a[0] == b[0] ? b[1].compareTo(a[1]) : a[0].compareTo(b[0]));
			splitter.forEach((match, key) -> {
				Pattern p = Pattern.compile(match, Pattern.DOTALL);
				Matcher m = p.matcher(content);
				while (m.find()) {
					Integer[] p1 = { m.start(), m.end() };
					Chunk chunk = new Chunk();
					chunk.name = key;
					chunk.value = m.group();
					matches.put(p1, chunk);
				}
			});
			List<Chunk> chunks = new ArrayList<>();
			int pos = 0;
			int id = 0;
			for (Map.Entry<Integer[], Chunk> match : matches.entrySet()) {
				int start = match.getKey()[0];
				if (pos > start) {
					chunks.add(match.getValue());
					continue;
				}
				if (pos < start) {
					Chunk chunk = new Chunk();
					chunk.id = ++id;
					chunk.value = content.substring(pos, start);
					chunks.add(chunk);
				}
				match.getValue().id = ++id;
				chunks.add(match.getValue());
				pos = match.getKey()[1];
			}
			if (pos < content.length()) {
				Chunk chunk = new Chunk();
				chunk.id = ++id;
				chunk.value = content.substring(pos);
				chunks.add(chunk);
			}
			return chunks;
		}

		public String getDefaultContent() throws IOException {
			return defaultContent == null ? null : IOUtils.read(new IOUtils.ReadFile(defaultContent));
		}

		@Override
		public boolean accept(File f) {
			return extFilter.accept(f);
		}

		@Override
		public String getDescription() {
			return extFilter.getDescription();
		}
	}
}
