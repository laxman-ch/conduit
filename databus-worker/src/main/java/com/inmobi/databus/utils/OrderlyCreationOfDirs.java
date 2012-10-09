package com.inmobi.databus.utils;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import com.inmobi.databus.utils.CalendarHelper;

/**
 * This class finds the out of order minute directories in various streams 
 * for different clusters. 
 * This class main function takes list of root dirs, base dirs, stream names
 *  as arguments. All are comma separated 
 */
public class OrderlyCreationOfDirs {
  private static final Log LOG = LogFactory.getLog(
      OrderlyCreationOfDirs.class);

  public OrderlyCreationOfDirs() {
  }
 
  /**
   * This method lists all the minute directories for a particular 
   * stream category.
   */
  public void doRecursiveListing(Path dir, Set<Path> listing, 
      FileSystem fs) throws IOException {
    FileStatus[] fileStatuses = fs.listStatus(dir);
    if (fileStatuses == null || fileStatuses.length == 0) {
      LOG.debug("No files in directory:" + dir);
      listing.add(dir);
    } else {
      for (FileStatus file : fileStatuses) {  
        if (file.isDir()) {
          doRecursiveListing(file.getPath(), listing,  fs);	      
        } else {
          listing.add(file.getPath().getParent());
        }       
      } 
    }
  }

  /**
   *  This method finds the out of order minute directories for a 
   *  particular stream.
   *  @param creationTimeOfFiles : TreeMap for all the directories statuses
   *   for a particular stream
   *  @param outOfOrderDirs : store out of order directories : outOfOrderDirs
   */
  public void validateOrderlyCreationOfPaths(
      TreeMap<Date , FileStatus> creationTimeOfFiles, 
      List<Path> outOfOrderDirs) {
    Date previousKeyEntry = null;
    for (Date presentKeyEntry : creationTimeOfFiles.keySet() ) {
      if (previousKeyEntry != null) {
        if (creationTimeOfFiles.get(previousKeyEntry).getModificationTime()
            > creationTimeOfFiles.get(presentKeyEntry).getModificationTime()) {
          System.out.println("Directory is created in out of order :    " + 
              creationTimeOfFiles.get(presentKeyEntry).getPath()); 
          outOfOrderDirs.add(creationTimeOfFiles.get(previousKeyEntry)
              .getPath());
        }
      }
      previousKeyEntry = presentKeyEntry;
    }
  }

  public void listingAndValidation(Path streamDir, FileSystem fs, 
      List<Path> outOfOrderDirs, Set<Path> notCreatedMinutePaths)
      throws IOException {
    Set<Path> listing = new HashSet<Path>();
    TreeSet<Path> hourPathListing = new TreeSet<Path>(new PathComparator());
    TreeSet<Path> minutePathListing = new TreeSet<Path>(new PathComparator());
    TreeMap<Date, FileStatus>creationTimeOfFiles = new TreeMap<Date, 
        FileStatus >();
    doRecursiveListing(streamDir, listing, fs);
    for (Path path :listing) {
      creationTimeOfFiles.put(CalendarHelper.getDateFromStreamDir(
          streamDir, path), fs.getFileStatus(path));
    }
    validateOrderlyCreationOfPaths(creationTimeOfFiles, outOfOrderDirs);
    createHourPathSetAndMinutePathSet(listing, hourPathListing,
        minutePathListing);
    validateMinuteFolderCreation(hourPathListing, minutePathListing,
        notCreatedMinutePaths);
  }
  
  public void createHourPathSetAndMinutePathSet(Set<Path> listing,
      TreeSet<Path> hourPathListing, TreeSet<Path> minutePathListing) {
    for (Path path : listing) {
      FileStatus fileStatus = null;
      try {
        FileSystem fs = path.getFileSystem(new Configuration());
        fileStatus = fs.getFileStatus(path);
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (fileStatus.isDir()) {
        minutePathListing.add(path);
        hourPathListing.add(path.getParent());
      } else {
        minutePathListing.add(path.getParent());
        hourPathListing.add(path.getParent().getParent());
      }
    }
  }

  public void validateMinuteFolderCreation(TreeSet<Path> hourPathListing,
      TreeSet<Path> minutePathListing, Set<Path> notCreatedMinutePaths) {
    for (Path path : hourPathListing) {
      if (path.compareTo(hourPathListing.last()) == 0) {
        int number = getNumberOfChildFolders(path);
        String minutePath;
        for (int i = 0; i < number; i++) {
          if (i < 10)
            minutePath = "0" + Integer.toString(i);
          else
            minutePath = Integer.toString(i);
          Path newPath = new Path(path, minutePath);
          if (!checkIfPresent(path, newPath))
            notCreatedMinutePaths.add(newPath);
        }
      } else {
        String minutePath;
        for (int i = 0; i < 60; i++) {
          if (i < 10)
            minutePath = "0" + Integer.toString(i);
          else
            minutePath = Integer.toString(i);
          Path newPath = new Path(path, minutePath);
          if (!checkIfPresent(path, newPath))
            notCreatedMinutePaths.add(newPath);
        }
      }
    }
  }
  
  public int getNumberOfChildFolders(Path path){
    FileSystem fs = null;
    try{
      fs= path.getFileSystem(new Configuration());
      return fs.listStatus(path).length;
    }
    catch(IOException e){
      e.printStackTrace();
    }
    return -1;
  }
  
  public boolean checkIfPresent(Path parentPath, Path childPath){
    try{
      FileSystem fs= parentPath.getFileSystem(new Configuration());
      FileStatus[] fileStatuses = fs.listStatus(parentPath);
      Path path;
      for(FileStatus fileStatus : fileStatuses){
        path = fileStatus.getPath();
        if(path.compareTo(childPath)==0)
          return true;
      }
    }
    catch(IOException e){
      e.printStackTrace();
    }
    return false;    
  }
  
  public void getStreamNames(String baseDir, String rootDir, List<String>
  		streamNames) throws Exception {
  		FileSystem baseDirFs = new Path(rootDir, baseDir).getFileSystem
  				(new Configuration());
  		FileStatus[] streamFileStatuses = baseDirFs.listStatus(new Path
  				(rootDir, baseDir));
  		for (FileStatus file : streamFileStatuses) {
  			if (!streamNames.contains(file.getPath().getName())) {
  				streamNames.add(file.getPath().getName());
  			}
  		}  	
  }
  
  public void getBaseDirs(String baseDirArg, List<String> baseDirs) {
  	for (String baseDir : baseDirArg.split(",")) {
			baseDirs.add(baseDir);
		}
  }
  
  public List<Path> run(String [] args) throws Exception {
  	List<Path> outoforderdirs = new ArrayList<Path>();
  	List<Path> notCreatedMinutePaths = new ArrayList<Path>();
  	if (args.length >= 1) {
  		String[]	rootDirs = args[0].split(",");
  		List<String> baseDirs = new ArrayList<String>();
  		List<String> streamNames;
  		if (args.length == 1) {
  			baseDirs.add("streams");
  			baseDirs.add("streams_local");
  			for (String rootDir : rootDirs) {
  				for (String baseDir : baseDirs) {
  					streamNames = new ArrayList<String>();
  					getStreamNames(baseDir, rootDir, streamNames);
  					outoforderdirs.addAll(pathConstruction(rootDir, baseDir, 
  							streamNames));
                    notCreatedMinutePaths.addAll(pathConstructionForMissingDirs(
                rootDir, baseDir, streamNames));
  				}
  			}
  			if (outoforderdirs.isEmpty()) {
  				System.out.println("There are no out of order dirs");
  			}
  			if (notCreatedMinutePaths.isEmpty()) {
  			    System.out.println("There are no missing dirs");
  			}
  			else
  			  for(Path path: notCreatedMinutePaths){
  	            System.out.println("Missing path: "+path.toString());  			    
  			  }
  		} else if (args.length == 2) {
  			getBaseDirs(args[1], baseDirs);
  			for (String rootDir : rootDirs) {
  				for (String baseDir : baseDirs) {
  					streamNames = new ArrayList<String>();
  					getStreamNames(baseDir, rootDir, streamNames);
  					outoforderdirs.addAll(pathConstruction(rootDir, baseDir, 
                        streamNames));
                    notCreatedMinutePaths.addAll(pathConstructionForMissingDirs(
                rootDir, baseDir, streamNames));
  				}
  			}
  			if (outoforderdirs.isEmpty()) {
  				System.out.println("There are no out of order dirs");
  			}
  			if (notCreatedMinutePaths.isEmpty()) {
  			    System.out.println("There are no missing dirs");
            }
  			else
              for(Path path: notCreatedMinutePaths){
                System.out.println("Missing path: "+path.toString());               
              }
  		} else if (args.length == 3) {
  			getBaseDirs(args[1], baseDirs);
  			streamNames = new ArrayList<String>();
  			for (String streamname : args[2].split(",")) {
  				streamNames.add(streamname);
  			}
  			for (String rootDir : rootDirs) {
  				for (String baseDir : baseDirs) {
  				  outoforderdirs.addAll(pathConstruction(rootDir, baseDir, 
                    streamNames));
  				  notCreatedMinutePaths.addAll(pathConstructionForMissingDirs(
  	                rootDir, baseDir, streamNames));
  				}
  			}
  			if (outoforderdirs.isEmpty()) {
  				System.out.println("There are no out of order dirs");
  			} 
  			if (notCreatedMinutePaths.isEmpty()) {
                System.out.println("There are no missing dirs");
            }
  			else
              for(Path path: notCreatedMinutePaths){
                System.out.println("Missing path: "+path.toString());               
              }
  		} 
  	} else {
  		System.out.println("Insufficient number of arguments: 1st argument:" +
  				" rootdirs," + " 2nd arument :basedirs, 3rd arguments: streamnames"
  				+ " 2nd arg, 3rd args are optionl here");
  		System.exit(1);
  	}
  	return outoforderdirs;
  }

  /**
   * @param  rootDirs : array of root directories
   * @param  baseDirs : array of baseDirs
   * @param  streamNames : array of stream names
   * @return outOfOrderDirs: list of out of directories for all the streams.
   */
  public List<Path> pathConstruction(String rootDir, String baseDir,
  		List<String> streamNames) throws IOException {
  	List<Path> outOfOrderDirs = new ArrayList<Path>();
  	Set<Path> notCreatedMinutePaths = new HashSet<Path>();
  	FileSystem fs = new Path(rootDir).getFileSystem(new Configuration());
  	Path rootBaseDirPath = new Path(rootDir, baseDir);
  	for (String streamName : streamNames) {
  		Path streamDir = new Path(rootBaseDirPath , streamName);
  		FileStatus[] files = fs.listStatus(streamDir);
  		if (files == null || files.length == 0) {
  			LOG.info("No direcotries in that stream: " + streamName);
  			continue;
  		}
        listingAndValidation(streamDir, fs, outOfOrderDirs, notCreatedMinutePaths);
  	}
  	return outOfOrderDirs;
  }

  public static void main(String[] args) throws Exception {
  	OrderlyCreationOfDirs obj = new OrderlyCreationOfDirs();
  	obj.run(args);
  }

  public Set<Path> pathConstructionForMissingDirs(
      String rootDir, String baseDir, List<String> streamNames) {
    List<Path> outOfOrderDirs = new ArrayList<Path>();
    Set<Path> notCreatedMinutePaths = new HashSet<Path>();
    try{
      FileSystem fs = new Path(rootDir).getFileSystem(new Configuration());
      Path rootBaseDirPath = new Path(rootDir, baseDir);
      for (String streamName : streamNames) {
          Path streamDir = new Path(rootBaseDirPath , streamName);
          FileStatus[] files = fs.listStatus(streamDir);
          if (files == null || files.length == 0) {
              LOG.info("No direcotries in that stream: " + streamName);
              continue;
          }
          listingAndValidation(streamDir, fs , outOfOrderDirs, notCreatedMinutePaths);
      }
    }
    catch(IOException e){
      e.printStackTrace();
    }
    return notCreatedMinutePaths;
  }
}

class PathComparator implements Comparator<Path>{
  
  @Override
  public int compare(Path p1, Path p2){
    Path parentP1 = p1.getParent();
    Path parentP2 = p2.getParent();
    if(parentP1.getName().compareTo(parentP2.getName()) == 0)
      return p1.getName().compareTo(p2.getName());
    else if(parentP1.getName().compareTo(parentP2.getName()) > 0)
      return 1;
    else
      return -1;
  }
}
