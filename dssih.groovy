/**
 * Copyright 2016 Jim Cornmell/Jim at JimsCosmos.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * @author Jim Cornmell [Jim at JimsCosmos.com]
 * @version 1.0.0
 */


class Main {
	private static final String DASHES = "----------------------------------------------------------------------------------"
	static Properties props = new Properties()
	static OptionAccessor options;

	/**
	 * Read command line arguments, load the properties and do something.
	 * 
	 * @param args Run it without any arguments to get help.
	 */
	static void main(String... args) {
		// Setup command line arguments.
		def cli = new CliBuilder(usage: 'dssih.groovy -[himp]')
		cli.with {
			h longOpt: 'help', 'Show usage information', required: false
			i longOpt: 'info', 'Information on the calibration images you have', required: false
			m longOpt: 'make', 'make image files', required: false
			f longOpt: 'folders', 'make folders that are needed', required: false
			p longOpt: 'properties', args:1, argName:'property_file', 'Name of properties file (default properties file="dssih.properties")', required: false
		}

		try {
			// Parse command line arguments.
			options = cli.parse(args)

			if (options) {
				// Load the properties file.
				new File(options.p ? options.p : 'dssih.properties').withInputStream { props.load(it) }

				if (options.f) {
					println "Making folders!"
				} else {
					println "NOT Making folders!"
				}

				// Run the program based on the arguments.
				if (options.m) {
					makeDssImageFileLists()
				} else if (options.i) {
					calibrationInformation()
				} else {
					cli.usage()
				}
			}
		} catch (Exception e) {
			println "\nERROR: $e\n"
			cli.usage()
		}
	}

	/**
	 * Get the master file name for a given iso directory, either flats or bias master.
	 *  
	 * @param dirIso Directory for the iso.
	 * @param type Either "Flat" or "Offset"
	 * @return File of a master, which may or may not actually exist. 
	 */
	private static File getMasterFile(File dirIso, String type) {
		return new File("${dirIso.path}/Master${type}_ISO"+dirIso.name.replace('iso','')+".tif")
	}

	/**
	 * Get the master file name for a given iso, temp and exp directory. 
	 * @param dirIso Directory for the iso.
	 * @param dirTemp Directory for the temperature.
	 * @param dirExp Directory for the exposure.
	 * @return File of a master, which may or may not actually exist. 
	 */
	private static File getMasterFileDarks(String exp, String iso, String temp) {
		return new File("${props.calibrationFolder}/darks/$exp/$iso/$temp/MasterDark_ISO"+iso.replace('iso','')+"_${exp}.tif");
	}

	/**
	 * Print out inventory of your calibration files.
	 */
	private static void calibrationInformation() {
		println DASHES
		println "CALIBRATION IMAGES INFORMATION\n\nFLATS:       ${props.calibrationFolder}/flats"
		new File("${props.calibrationFolder}/flats").eachDir() { dirScope ->
			println "\t${dirScope.name}"
			dirScope.eachDir() { dirIso ->
				int count = dirIso.listFiles().count{ it.name ==~ /.*\.${props.imageExtension}/ }
				printf("\t\t%20s%10d files", dirIso.name, count)

				// Check for the master file.
				File masterFile = getMasterFile(dirIso, "Flat")
				println "\t" + (masterFile.exists() ? masterFile.name : "(no master flat)");
			}
		}

		println "\nBIAS-OFFSET: ${props.calibrationFolder}/bias_offset"
		new File("${props.calibrationFolder}/bias_offset").eachDir() { dirIso ->
			int count = dirIso.listFiles().count{ it.name ==~ /.*\.${props.imageExtension}/ }
			printf("\t\t%20s%10d files", dirIso.name, count)

			// Check for the master file.
			File masterFile = getMasterFile(dirIso, "Offset");
			println "\t" + (masterFile.exists() ? masterFile.name : "(no master bias)");
		}

		println "\nDARKS:       ${props.calibrationFolder}/darks"
		new File("${props.calibrationFolder}/darks").eachDir() { dirExp ->
			println "\t${dirExp.name}"

			dirExp.eachDir() { dirIso ->
				printf("\t\t%20s\n", dirIso.name)

				dirIso.eachDir() { dirTemp ->
					int count = dirTemp.listFiles().count{ it.name ==~ /.*\.${props.imageExtension}/ }
					printf("\t\t%25s%5d files", dirTemp.name, count)

					// Check for the master file.
					File masterFile = getMasterFileDarks(dirExp.name, dirIso.name, dirTemp.name)
					println "\t" + (masterFile.exists() ? masterFile.name : "(no master dark)");
				}
			}
		}

		println DASHES
	}

	/**
	 * Make image file lists.
	 * 
	 * @param folder Your calibration image root folder.
	 */
	private static void makeDssImageFileLists() {
		File doAll = new File("${props.resultsImageFolder}\\PROCESS_ALL.BAT")
		
		if (doAll.exists()) {
			doAll.delete()
		}
		
		doAll << "@ECHO OFF\n\n"
		String dosCommands = ""
		String fileListsFiles = ""

		println DASHES
		println "Searching for unprocessed imaging sessions in folder:\n\t${props.rawImageFolder}/<CATALOGUE>/<OBJECT>/<IMAGEING_SESSION>"

		new File(props.rawImageFolder).eachDir() { dirCat ->
			if (dirCat.name == "Archive") {
				println "Archive is ignored"
				return
			}
			
			println dirCat.name
			
			dirCat.eachDir() { dirObj ->
				println "\t${dirObj.name}"
				
				dirObj.eachDir() { dirImagingSession ->
					// Split folder name into parts.
					def parts = (dirImagingSession.name =~ /([^_]+)_(\d+)iso-(\d+)s-(\d+)-(\d+)-(\d+)/)
					def cam=parts[0][1]
					def iso=parts[0][2]
					def exp=parts[0][3]
					def y=parts[0][4]
					def m=parts[0][5]
					def d=parts[0][6]
					
					// Find number of exposures in the folder.
					def numexp=dirImagingSession.listFiles().count { it.name ==~ /.*.CR2/ }
			
					// Make new filename.
					String fileName = "${dirCat.name}_${dirObj.name}_${cam}_${iso}iso-${exp}s_x_${numexp}-${y}-${m}-${d}"
					println fileName
					
					if (new File("${props.resultsImageFolder}\\${fileName}.tif").exists()) {
						println "\t\t\tDONE    - ${fileName}: Session already processed, remove tif to process again."
					} else {
						String fileListString = doImagingSession(dirCat.name, dirObj.name, dirImagingSession)

						if (fileListString != null) {
							doAll << "CALL \"${fileName}.bat\"\n"
							File dosFile = makeDosCommand(fileName, dirImagingSession.canonicalPath)
							dosCommands += "\t\t\t${dosFile.name}\n"
							File listFile = makeFileList(fileName, fileListString)
							fileListsFiles += "\t\t\t${listFile.name}\n"
						}
					}
				}
			}
		}

		printNextSteps(doAll, fileListsFiles, dosCommands)
	}

	/**
	 * Print the next steps.
	 * 
	 * @param processAllCommand
	 * @param fileListsFiles
	 * @param dosCommands
	 * @return
	 */
	private static printNextSteps(File processAllCommand, String fileListsFiles, String dosCommands) {
		println DASHES
		println "Legend:"
		println "\tMISSING = Cannot process this file as some calibration images are missing, i.e. flats..."
		println "\tDONE    = We already have a tif file, so no need to do anything"
		println "\tGOOD    = We have everything needed to process this, see option 3 below"
		println DASHES
		
		if (fileListsFiles == "") {
			println "All images processed there is nothing to do!"
			println DASHES
			println "Removing ${processAllCommand.name}"
			processAllCommand.delete()			
			return
		}
		
		println "You now have 3 options\n"
		println "  - OPTION 1: Process all of the files completely automatically by running the following command:\n"
		println "\t\t\t${processAllCommand.name}\n"
		println "  - OPTION 2: Process a single file list manually in DSS by using the \"Open a File List...\" menu option:\n"
		println "\t\tThe available image file list files are:"
		println fileListsFiles
		println "  - OPTION 3: Process a single file list automatically by running any of the individual BATCH files\n"
		println "\t\tThe available image file list command files are:"
		println dosCommands
		println DASHES
	}

	/**
	 * Make file list for a single imaging session.
	 * 
	 * @param nameCat
	 * @param nameObject
	 * @param dirImagingSession
	 */
	private static String doImagingSession(String nameCat, String nameObject, File dirImagingSession) {
		String scope="UNKNOWN"
		boolean ok=true
		int count=0
		int totalExp=0
		String fileListString="DSS file list\nCHECKED\tTYPE\tFILE\n"
		String iso=""
		String temp=""
		String exp=""

		if (dirImagingSession.name =~ /^[^_]+_/) {
			scope = (dirImagingSession.name =~ /^([^_]+)_/)[0][1]
		} else {
			ok=false
		}

		dirImagingSession.eachFile() { dirImage ->
			if (dirImage.name.endsWith(".${props.imageExtension}")) {
				if (dirImage.name =~ /_\d+s/) {
					exp = (dirImage.name =~ /_(\d+)s_/)[0][1]
					totalExp+=exp as Integer
					exp += "s"
				} else {
					ok=false
				}

				if (dirImage.name =~ /_\d+iso_/) {
					iso = (dirImage.name =~ /_(\d+iso)_/)[0][1]
				} else {
					ok=false
				}

				if (dirImage.name =~ /_[-+]\d+c_/) {
					temp = (dirImage.name =~ /_([-+]\d+c)_/)[0][1]
				} else {
					ok=false
				}

				if (ok) {
					fileListString += "1\tlight\t${dirImage.canonicalPath}\n"
				} else {
					println "\t\t\t\tERROR: ***************************** ${dirImage.name} : ISO=$iso TEMP=$temp EXP=$exp SCOPE=$scope"
				}

				count++
			}
		}

		try {
			fileListString += makeListFlats(iso, scope)
		} catch (Exception e) {
			if (props.defaultIso) {
				try{
					fileListString += makeListFlats(props.defaultIso, scope)
				} catch (Exception ee) {
					ok=false
					println "\t\t\tMISSING - ${dirImagingSession.name} : ${ee.message}"
				}
			} else {
				ok=false
				println "\t\t\tMISSING - ${dirImagingSession.name} : ${e.message}"
			}
		}

		try {
			fileListString += makeListOffsetBias(iso)
		} catch (Exception e) {
			if (props.defaultIso) {
				try{
					fileListString += makeListOffsetBias(props.defaultIso)
				} catch (Exception ee) {
					ok=false
					println "\t\t\tMISSING - ${dirImagingSession.name} : ${ee.message}"
				}
			} else {
				ok=false
				println "\t\t\tMISSING - ${dirImagingSession.name} : ${e.message}"
			}
		}

		try {
			fileListString += makeListDarks(exp, iso, temp)
		} catch (Exception e) {
			if (props.defaultIso) {
				try{
					fileListString += makeListDarks(props.defaultExposure, props.defaultIso, props.defaultTemperature)
				} catch (Exception ee) {
					ok=false
					println "\t\t\tMISSING - ${dirImagingSession.name} : ${ee.message}"
				}
			} else {
				ok=false
				println "\t\t\tMISSING - ${dirImagingSession.name} : ${e.message}"
			}
		}

		if (ok) {
			def mins=(totalExp/60)
			println "\t\t\tGOOD    - ${dirImagingSession.name} : OK to process: $count images, $totalExp seconds ($mins minutes) taken with $scope"
			return fileListString
		}

		return null
	}

	/**
	 * Make a script to run this in MSDOS.
	 *
	 * @param fileName Base filename to use.
	 * @param folder Where the final tif is.
	 */
	private static File makeDosCommand(String fileName, String folder) {
		File runCmd = new File("${props.resultsImageFolder}\\${fileName}.bat")
		runCmd.delete()
		runCmd << "@ECHO OFF\n\n"

		runCmd << "ECHO ------------------------------------------------------------------------------------------------\n"
		runCmd << "ECHO Run DSS command line with options from the properties.\n"
		runCmd << "\"${props.dssClPath}\" ${props.dssOptions} /O:\"${fileName}\" \"${fileName}_file_list.txt\"\n\n"

		runCmd << "ECHO Move the resultant image to the results folder\n"
		runCmd << "MOVE \"${folder}\\${fileName}.tif\" \"${props.resultsImageFolder}\"\n\n"
		runCmd << "ECHO ------------------------------------------------------------------------------------------------\n"
		return runCmd
	}

	/**
	 * Make the file list text file, append the properties if they are defined.
	 * 
	 * @param fileName Base file name.
	 * @param fileListText The text.
	 */
	private static File makeFileList(String fileName, String fileListText) {
		File fileList = new File("${props.resultsImageFolder}\\${fileName}_file_list.txt")
		fileList.delete()
		fileList << fileListText

		// Finally append the configuration, if defined and if it exists
		if (props.dssConfig) {
			File dssConfig = new File(props.dssConfig)
			if (dssConfig.exists()) {
				fileList << dssConfig.text
			}
		}

		return fileList
	}

	/**
	 * Get file list of flats.
	 * 
	 * @param iso
	 * @param scope
	 * @return file list
	 */
	private static String makeListFlats(String iso, String scope) {
		String string = ""
		File dirIso = new File("${props.calibrationFolder}/flats/$scope/$iso")
		File masterFile = getMasterFile(dirIso, "Flat")

		if (masterFile.exists()) {
			string = "1\tflat\t${masterFile.canonicalPath}\n"
		} else {
			try {
				dirIso.eachFileMatch(~/^.*\.${props.imageExtension}/) { string += "1\tflat\t${it.canonicalPath}\n" }
			} catch (FileNotFoundException e) {
				if (options.f) {
					dirIso.mkdirs()
				}
				throw new Exception("Need FLAT files for $iso and $scope")
			}

			if (string == "") {
				throw new Exception("Need FLAT files for $iso and $scope")
			}
		}

		return string
	}

	/**
	 * Get file list of offset.
	 * 
	 * @param iso
	 * @return file list
	 */
	private static String makeListOffsetBias(String iso) {
		String string = ""
		File dirIso = new File("${props.calibrationFolder}/bias_offset/$iso")
		File masterFile = getMasterFile(dirIso, "Offset")

		if (masterFile.exists()) {
			string = "1\toffset\t${masterFile.canonicalPath}\n"
		} else {
			try {
				dirIso.eachFileMatch(~/^.*\.${props.imageExtension}/) { string += "1\toffset\t${it.canonicalPath}\n" }
			} catch (FileNotFoundException e) {
				if (options.f) {
					dirIso.mkdirs()
				}
				throw new Exception("Need OFFSET/BIAS files for $iso")
			}

			if (string == "") {
				throw new Exception("Need OFFSET/BIAS files for $iso")
			}
		}
		return string
	}

	/**
	 * Get file list of darks.
	 * 
	 * @param exp
	 * @param iso
	 * @param temp
	 * @return file list
	 */
	private static String makeListDarks(String exp, String iso, String temp) {
		String string = ""
		File masterFile = getMasterFileDarks(exp, iso, temp)

		if (masterFile.exists()) {
			string = "1\tdark\t${masterFile.canonicalPath}\n"
		} else {
			File dirTemp = new File("${props.calibrationFolder}/darks/$exp/$iso/$temp")

			try {
				if (!dirTemp.exists()) {
					temp=props.defaultTemperature
				}

				dirTemp.eachFileMatch(~/^.*\.${props.imageExtension}/) { string += "1\tdark\t${it.canonicalPath}\n" }
			} catch (FileNotFoundException e) {
				if (options.f) {
					dirTemp.mkdirs()
				}
				throw new Exception("Need DARK files for $iso, $exp and $temp")
			}

			if (string == "") {
				throw new Exception("Need DARK files for $iso, $exp and $temp")
			}
		}

		return string
	}
}
