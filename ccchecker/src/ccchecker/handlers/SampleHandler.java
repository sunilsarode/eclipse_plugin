package ccchecker.handlers;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class Info {
	String str;
	int line;

	public Info(String string, int loc_line) {
		this.line = loc_line;
		this.str = string;
	}

}

public class SampleHandler extends AbstractHandler {

	private static String toolpath = "";
	private static String warningJsonFilePath = "";

	static Map<Integer, IMarker> map = new LinkedHashMap<>();
	static String partAtivated = "";
	static boolean partActive = false;
	private String ERROR="error generated."; 
	private String ERRORS="errors generated.";

	public SampleHandler() {

		List<String> pathlist = readFromFile();
		toolpath = pathlist.get(0);
		warningJsonFilePath = pathlist.get(1);
	}

	private List<String> readFromFile() {

		List<String> pathlist = new ArrayList<>();
		// Open the file
		InputStream fstream = null;
		try {

			fstream = getClass().getResourceAsStream("/config.txt");

			BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

			String strLine;

			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				pathlist.add(strLine.trim());
			}

			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// Close the input stream
			try {
				if (fstream != null)
					fstream.close();

			} catch (IOException e) {

				e.printStackTrace();
			}
		}

		return pathlist;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IEditorPart editor1 = page.getActiveEditor();
		if (editor1 == null) {
			return null;
		}
		// will throw null pointer exception if no editor is open in workbench
		IEditorInput input_start = editor1.getEditorInput();
		IPath path_start = input_start instanceof FileEditorInput ? ((FileEditorInput) input_start).getPath() : null;
		IFile file_startFile = ((FileEditorInput) editor1.getEditorInput()).getFile();

		// A part service tracks the creation and activation of parts within a workbench
		// page.

		IPartService partService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService();

		ICommandService commandService = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
		Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_SAVE);

		System.out.println(partService.getActivePart().getTitle());

		// on start of eclipse partlistener is not working ,dirty fix added using
		// property listener

		partService.getActivePart().addPropertyListener(new IPropertyListener() {

			@Override
			public void propertyChanged(Object arg0, int arg1) {

				// System.out.println(" it is start"); uncomment for testing
				Job job = new Job("My job") {

					@Override
					protected IStatus run(IProgressMonitor arg0) {

						if (!editor1.isDirty()) {
							if (path_start.toString().contains(partAtivated) && !partActive) {
								List<Info> inforlist = runCCChecker(path_start, editor1);
								syncWithUi(inforlist, file_startFile);
							}

						}
						return Status.OK_STATUS;
					}

				};

				job.setUser(true);
				job.schedule();
			}
		});

		partService.addPartListener(new IPartListener() {

			@Override
			public void partActivated(IWorkbenchPart part) {

				partActive = true;

				if (part instanceof IEditorPart) {
					System.out.println("partAcivated " + part.getTitle());
					partAtivated = part.getTitle();
					IEditorPart editor = (IEditorPart) part;

					IEditorInput input = editor.getEditorInput();

					IDocument document = (((ITextEditor) editor).getDocumentProvider()).getDocument(input);

					IFile file = ((FileEditorInput) editor.getEditorInput()).getFile();

					IPath path = input instanceof FileEditorInput ? ((FileEditorInput) input).getPath() : null;

					// System.out.println(path);
					if (path != null) {

						command.addExecutionListener(new IExecutionListener() {

							@Override
							public void preExecute(String arg0, ExecutionEvent event) {

								Job job = new Job("My job") {

									@Override
									protected IStatus run(IProgressMonitor arg0) {

										if (!editor.isDirty()) {
											if (path.toString().contains(partAtivated)) {
												List<Info> inforlist = runCCChecker(path, editor);
												syncWithUi(inforlist, file);
											}

										}
										return Status.OK_STATUS;
									}

								};

								job.setUser(true);
								job.schedule();
							}

							@Override
							public void postExecuteSuccess(String arg0, Object arg1) {

								Job job = new Job("My job") {

									@Override
									protected IStatus run(IProgressMonitor arg0) {
										System.out.println("Ctrs+s " + editor.isDirty());
										if (!editor.isDirty()) {

											if (path.toString().contains(partAtivated)) {
												List<Info> inforlist = runCCChecker(path, editor);
												syncWithUi(inforlist, file);
											}
										}
										return Status.OK_STATUS;
									}

								};

								job.setUser(true);
								job.schedule();
							}

							@Override
							public void postExecuteFailure(String arg0, ExecutionException arg1) {
								// TODO Auto-generated method stub

							}

							@Override
							public void notHandled(String arg0, NotHandledException arg1) {
								// TODO Auto-generated method stub

							}
						});

					}

				}
			}

			@Override
			public void partBroughtToTop(IWorkbenchPart arg0) {
				// TODO Auto-generated method stub
				System.out.println("partBroughtToTop " + arg0.getTitle());
			}

			@Override
			public void partClosed(IWorkbenchPart arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void partDeactivated(IWorkbenchPart arg0) {
				// TODO Auto-generated method stub
				System.err.println("partDeactivated " + arg0.getTitle());

			}

			@Override
			public void partOpened(IWorkbenchPart part) {
				// TODO Auto-generated method stub

			}

		});

		return null;
	}

	public static void createMarkerForResource(IResource resource, int linenumber, String message)
			throws CoreException {

		/*
		 * if we have new message at same line number,append it with new message else
		 * create new message
		 */
		if (map.get(linenumber) != null) {

			IMarker marker = map.get(linenumber);
			String existing_message = (String) marker.getAttribute(IMarker.MESSAGE);
			
			existing_message += "\n" + message;

			marker.setAttribute(IMarker.MESSAGE, existing_message);
			map.put(linenumber, marker);
		} else {
			IMarker marker = resource.createMarker(IMarker.PROBLEM);
			marker.setAttribute(IMarker.LINE_NUMBER, linenumber);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
			marker.setAttribute(IMarker.MESSAGE, "CCCheck:" + message);
			map.put(linenumber, marker);
		}
	}

	private void syncWithUi(List<Info> inforlist, IFile file) {

		// Update the user interface asynchronously
		Display.getDefault().syncExec(new Runnable() {

			// ... do any work that updates the screen ...

			public void run() {

				try {

					// System.out.println("Number of warnings " + inforlist.size());
					
					/**
					 * if input list size is zero it mean there is some compile time error remove all markers  */
					if(inforlist.size()==0) {
						for (IMarker m : ((IResource) file).findMarkers(IMarker.PROBLEM, true, 1)) {
							if (m != null) {
								m.delete();
							}

						}
						return;
					}
					
					/**
					 * remove previous markers
					 */
					for (IMarker m : ((IResource) file).findMarkers(IMarker.PROBLEM, true, 1)) {
						if (m != null) {
							String str = (String) m.getAttribute(IMarker.MESSAGE);
							if (str != null) {
								if (str.startsWith("CCCheck:")) {
									m.delete();
								}
							}

						}

					}
					map.clear();

					System.out.println(file);
					IResource resource = (IResource) file;

					for (Info info : inforlist) {

						createMarkerForResource(resource, info.line, info.str);
					}

				} catch (CoreException e1) {
					e1.printStackTrace();
				}

			}
		});

	}

	/*
	 * here command is our clang tool with name main,
	 * 
	 * you add its directory location according to your directory location
	 * 
	 * arg1 is directory location of test file
	 */
	List<Info> runCCChecker(IPath path, IEditorPart editor) {

		List<Info> list = new ArrayList<>();
		String command = toolpath;

		ProcessBuilder pb = new ProcessBuilder(command, path.toString());

		Process process;

		try {

			process = pb.start();
			process.waitFor();
			InputStream error = process.getErrorStream();
		
			BufferedReader in = new BufferedReader(new InputStreamReader(error));
			String line = null;
		    String lastLine = "";
		    int countLine=0;
			while((line = in.readLine()) != null) {
				System.out.println("line "+countLine+" "+lastLine);
				countLine++;
			  lastLine=line;
			}
			System.out.println(lastLine);
			
			/**
			 * if file have compile time error return empty list 
			 * When you have last line of error stream as "error generated." or "errors generated." ,it mean there is some compile time error 
			 */
			if(lastLine.contains(ERROR)||lastLine.contains(ERRORS)) {
				
				return list;
			}
			
			JSONParser parser = new JSONParser();

			try {
				FileReader fileReader = new FileReader(warningJsonFilePath);

				JSONArray jsonArray = (JSONArray) parser.parse(fileReader);

				for (Object o : jsonArray) {
					JSONObject jsonobj = (JSONObject) o;

					int loc_line = Integer.parseInt(String.valueOf(jsonobj.get("loc_line")));
					int loc_column = Integer.parseInt(String.valueOf(jsonobj.get("loc_column")));

					String message = String.valueOf(jsonobj.get("message"));

					Info info = new Info(message + " " + loc_line + " " + loc_column, loc_line);
					list.add(info);

				}

			} catch (ParseException e1) {

				e1.printStackTrace();

			}

		} catch (IOException | InterruptedException e1) {
			e1.printStackTrace();
		}

		return list;
	}
}
