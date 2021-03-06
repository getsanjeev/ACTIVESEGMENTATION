package activeSegmentation.io;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import activeSegmentation.IProjectManager;
import activeSegmentation.Common;
import activeSegmentation.IDataSet;

import activeSegmentation.learning.WekaDataSet;
import weka.core.Instances;

public class ProjectManagerImp implements IProjectManager {

	private IDataSet dataSet;
	private static ProjectInfo projectInfo;
	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	private String activeSegDir;
	private Map<String,String> projectDir=new HashMap<String,String>();
	/**
	 * Read ARFF file
	 * @param filename ARFF file name
	 * @return set of instances read from the file
	 */
	public IDataSet readDataFromARFF(String filename){
		try{
			BufferedReader reader = new BufferedReader(
					new FileReader(filename));
			try{
				Instances data = new Instances(reader);
				// setting class attribute
				data.setClassIndex(data.numAttributes() - 1);
				reader.close();
				return new WekaDataSet(data);
			}
			catch(IOException e){IJ.showMessage("IOException");}
		}
		catch(FileNotFoundException e){IJ.showMessage("File not found!");}
		return null;
	}

	@Override
	public IDataSet getDataSet() {

		return  dataSet;
	}

	@Override
	public void setData(IDataSet data) {
		this.dataSet = data.copy();
	}

	@Override
	/**
	 * Write current instances into an ARFF file
	 * @param data set of instances
	 * @param filename ARFF file name
	 */
	public boolean writeDataToARFF(Instances data, String filename)
	{
		BufferedWriter out = null;
		try{
			out = new BufferedWriter(
					new OutputStreamWriter(
							new FileOutputStream( projectInfo.getProjectPath()+filename ) ) );

			final Instances header = new Instances(data, 0);
			out.write(header.toString());

			for(int i = 0; i < data.numInstances(); i++)
			{
				out.write(data.get(i).toString()+"\n");
			}
		}
		catch(Exception e)
		{
			IJ.log("Error: couldn't write instances into .ARFF file.");
			IJ.showMessage("Exception while saving data as ARFF file");
			e.printStackTrace();
			return false;
		}
		finally{
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return true;

	}





	@Override
	public boolean loadProject(String fileName) {
		// TODO Auto-generated method stub
		//System.out.println("IN LOAD PROJCT");
		setDirectory();
		//IJ.log(System.getProperty("plugins.dir"));
		if(projectInfo==null){
			ObjectMapper mapper = new ObjectMapper();
			try {
				projectInfo= mapper.readValue(new File(fileName), ProjectInfo.class);
				projectInfo.setPluginPath(activeSegDir);
				//metaInfo.setPath(path);
				//System.out.println("done");

			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}			


		}

		return true;
	}

	@Override
	public void writeMetaInfo( ProjectInfo project) {
		updateMetaInfo(projectInfo);
		ObjectMapper mapper = new ObjectMapper();
		try {
			projectInfo.setModifyDate(dateFormat.format(new Date()));
			if(projectInfo.getCreatedDate()==null){
				projectInfo.setCreatedDate(dateFormat.format(new Date()));
			}
			//System.out.println("SAVING");
			mapper.writeValue(new File(projectInfo.getProjectPath()+"/"+projectInfo.getProjectName()+"/"+projectInfo.getProjectName()+".json"), projectInfo);

			//System.out.println("DONE");

		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ProjectInfo getMetaInfo() {

		return this.projectInfo;
	}

	@Override
	public String createProject(String projectName, String projectType,String projectDirectory, 
			String projectDescription,
			String trainingImage){
		String message="done";
		String returnedMessage=validate(projectName,projectDirectory,trainingImage);
		if(!returnedMessage.equalsIgnoreCase(message)) {
			return returnedMessage;
		}
		setDirectory();
		projectInfo= new ProjectInfo();
		projectInfo.setProjectPath(projectDirectory);
		projectInfo.setProjectName(projectName);
		projectInfo.setProjectType(projectType);
		projectInfo.setProjectDescription(projectDescription);
		projectInfo.setPluginPath(activeSegDir);
		//DEFAULT 2 classes
		projectInfo.setClasses(2);
		createProjectSpace(projectDirectory,projectName);
		//CURRENT IMAGE
		if(null !=WindowManager.getCurrentImage()) {
			ImagePlus image= WindowManager.getCurrentImage();
			IJ.log(Integer.toString(image.getStackSize()));
			IJ.log(image.getTitle());
            createImages(image.getTitle(), image);
		}else { // TRAINING IMAGE FOLDER
			List<String> images=loadImages(trainingImage);
			for(String image: images) {
				ImagePlus currentImage=IJ.openImage(trainingImage+"/"+image);
				createImages(image, currentImage);
			}
		}

		projectInfo.setProjectDirectory(projectDir);
		writeMetaInfo(projectInfo);
		return message;
	}


	private void createImages(String image, ImagePlus currentImage) {
		String format=image.substring(image.lastIndexOf("."));
		String folder=image.substring(0, image.lastIndexOf("."));	
		if(currentImage.getStackSize()>0) {
			createStackImage(currentImage,format,folder);
		}else {
			createDirectory(projectDir.get(Common.FILTERSDIR)+folder);
			IJ.saveAs(currentImage,format,projectDir.get(Common.IMAGESDIR)+folder);

		}

	}
	private void createStackImage(ImagePlus image,String format, String folder) {
		IJ.log("createStack");
		//String format=image.getTitle().substring(image.getTitle().lastIndexOf("."));
		//String folder=image.getTitle().substring(0, image.getTitle().lastIndexOf("."));
		IJ.log(format);
		for(int i=1; i<=image.getStackSize();i++) {
			ImageProcessor processor= image.getStack().getProcessor(i);
			String title= folder+i;
			IJ.log(folder);
			IJ.log(title);
			createDirectory(projectDir.get(Common.FILTERSDIR)+title);
			IJ.saveAs(new ImagePlus(title, processor),format,projectDir.get(Common.IMAGESDIR)+title);
		}
		IJ.log("createStackdone");
	}
	private String validate(String projectName,String projectDirectory, 
			String trainingImage) {
		String message="done";
		if(projectName==null|| projectName.isEmpty()) {
			return " Project Name cannot be Empty";

		} else if(projectDirectory==null|| projectDirectory.isEmpty() || projectDirectory.equalsIgnoreCase(trainingImage)) {
			return "Project Directory cannot be Empty and Should not be same as training image directory";
		}
		else if (null == WindowManager.getCurrentImage() &&(trainingImage==null|| trainingImage.isEmpty())) {
			return "Training cannot be Empty and should be either tif file or folder with tiff images are"
					+ "located";
		}
		return message;
	}

	private void setDirectory() {
		//IJ.debugMode=true;
		String OS = System.getProperty("os.name").toLowerCase();
		IJ.log(OS);
		if( (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0 )) {
			activeSegDir=System.getProperty("plugins.dir")+"//plugins//activeSegmentation//";
		}
		else {
			activeSegDir=System.getProperty("plugins.dir")+"\\plugins\\activeSegmentation\\";	
		}

		//System.out.println(System.getProperty("plugins.dir"));
	}

	private void createProjectSpace(String projectDirectory, String projectName) {

		String projectString=projectDirectory+"/"+projectName+"/"+"Training";
		projectDir.put(Common.PROJECTDIR, projectString);
		projectDir.put(Common.FILTERSDIR, projectString+"/filters/");
		projectDir.put(Common.FEATURESDIR, projectString+"/features/");
		projectDir.put(Common.LEARNINGDIR, projectString+"/learning/");
		projectDir.put(Common.EVALUATIONDIR,projectString+"/evaluation/");
		projectDir.put(Common.IMAGESDIR,projectString+"/images/");
		createDirectory(projectDir.get(Common.PROJECTDIR));
		createDirectory(projectDir.get(Common.FILTERSDIR));
		createDirectory(projectDir.get(Common.FEATURESDIR));
		createDirectory(projectDir.get(Common.LEARNINGDIR));
		createDirectory(projectDir.get(Common.EVALUATIONDIR));
		createDirectory(projectDir.get(Common.IMAGESDIR));
		IJ.log("DONE");
	}

	private List<String> loadImages(String directory){
		List<String> imageList= new ArrayList<String>();
		File folder = new File(directory);
		File[] images = folder.listFiles();
		for (File file : images) {
			if (file.isFile()) {
				imageList.add(file.getName());
			}
		}
		return imageList;
	}
	private boolean createDirectory(String project){

		File file=new File(project);
		if(!file.exists()){
			file.mkdirs();
		}
		return true;
	}

	 public void updateMetaInfo(ProjectInfo project)
	  {
	    projectInfo = project;
	  }


}
