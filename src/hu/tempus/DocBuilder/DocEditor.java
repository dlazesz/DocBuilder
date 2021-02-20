package hu.tempus.DocBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

import hu.tempus.DocBuilder.DocFilter.Chunk;
import hu.tempus.HtmlGui.IOUtils;

public class DocEditor {

	protected static final AtomicInteger LAST_FILE_ID = new AtomicInteger(0);
	protected static final ConcurrentMap<Integer, DocEditor> FILES = new ConcurrentHashMap<>();

	protected static final Map<Integer, DocFilter> TEMPLATES = new HashMap<>();

	protected final Integer mFileId;
	protected final Template mTemplate;
	protected List<Chunk> mChunks;
	protected static String mError = "";

	protected DocEditor(Integer fileId, Template tpl) {
		mFileId = fileId;
		mTemplate = tpl;
	}

	public static void addTemplate(File file) {
		DocFilter filter = new DocFilter(file);
		TEMPLATES.put(TEMPLATES.size() + 1, filter);
	}

	public static DocEditor load(Integer fileId) {
		return FILES.get(fileId);
	}

	public String getLastError() {
		return mError;
	}

	public Integer getId() {
		return mFileId;
	}

	public List<Chunk> getChunks() {
		return mChunks;
	}

	public List<String> getJS() {
		return mTemplate.filter.getJS();
	}

	public List<String> getCSS() {
		return mTemplate.filter.getCSS();
	}

	public void setChunks(JsonArray chunks) {
		Iterator<Chunk> cit = mChunks.iterator();
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
		for (Map.Entry<Integer, DocEditor> f : FILES.entrySet()) {
			if (tpl.equals(f.getValue().mTemplate)) {
				return f.getValue();
			}
		}
		DocEditor editor = new DocEditor(LAST_FILE_ID.incrementAndGet(), tpl);
		if (!editor.parseFile(tpl)) {
			return editor;
		}
		FILES.put(editor.mFileId, editor);
		return editor;
	}

	public boolean save(boolean create) {
		mError = "";
		if (create) {
			Template tpl = choose(mTemplate, "Save the file", "Save");
			if (tpl == null)
				return false;
			return buildFile(tpl.file);
		}
		return buildFile(mTemplate.file);
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

			for (DocFilter tpl : TEMPLATES.values()) {
				chooser.addChoosableFileFilter(tpl);
			}
			if (TEMPLATES.size() > 0) {
				chooser.setFileFilter(current != null ? current.filter : TEMPLATES.get(1));
				chooser.setAcceptAllFileFilterUsed(false);
			}

			int result = chooser.showDialog(frame, button);
			if (result == JFileChooser.APPROVE_OPTION) {
				return new Template((DocFilter) chooser.getFileFilter(), chooser.getSelectedFile());
			}
		} catch (Exception e) {
			mError = e.getMessage();
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
					mError = "This template does not support creating files";
					return false;
				}
			} else {
				content = IOUtils.read(new FileInputStream(tpl.file));
			}
			mChunks = tpl.filter.getChunks(content);
			return true;
		} catch (Exception e) {
			mError = e.getMessage();
			e.printStackTrace(System.err);
		}
		return false;
	}

	synchronized public boolean buildFile(File file) {
		try (OutputStream os = new FileOutputStream(file, false)) {
			for (Chunk chunk : mChunks) {
				os.write(chunk.value.getBytes("UTF-8"));
			}
			return true;
		} catch (Exception e) {
			mError = e.getMessage();
			e.printStackTrace(System.err);
		}
		return false;
	}

	protected static class Template {
		public final DocFilter filter;
		public final File file;

		public Template(DocFilter filter, File file) {
			this.filter = filter;
			this.file = file;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Template))
				return false;
			Template tpl = (Template) o;
			if (tpl.filter != filter || !tpl.file.equals(file)) {
				return false;
			}
			return true;
		}

	}

}
