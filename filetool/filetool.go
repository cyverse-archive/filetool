package main

import (
	"bytes"
	"exec"
	"flag"
	"fmt"
	"io/ioutil"
	"iplant/ftutils"
	"os"
	"os/user"
	"path"
	"path/filepath"
	"strings"
)

const IPUT = "iput"
const IMKDIR = "imkdir"
const ILS = "ils"
const IGET = "iget"
const IMETA = "imeta"

var excludeFiles []string
var excludeFilesRaw string
var excludeFilesDelimiter string
var includeFiles []string
var includeFilesRaw string
var includeFilesDelimiter string
var source string
var dest string
var avuAttr string
var avuVal string
var avuUnit string
var singleThreaded bool
var avuOp bool
var getOp bool
var mkdirOp bool

/*
 * Sets up the flags and parses the command-line.
 */
func SetupFlags() {
	flag.StringVar(&excludeFilesRaw, "exclude", "", "List of input files in the analysis.")
	flag.StringVar(&excludeFilesDelimiter, "exclude-delimiter", ",", "Delimiter for the list of input files.")
	flag.StringVar(&includeFilesRaw, "include", "", "List of files to transfer.")
	flag.StringVar(&includeFilesDelimiter, "include-delimiter", ",", "Delimiter for the list of files to transfer.")
	flag.StringVar(&source, "source", ".", "The directory containing files to be transferred.")
	flag.StringVar(&dest, "destination", "", "The destination directory in iRODS.")
	flag.BoolVar(&singleThreaded, "single-threaded", false, "Tells the iRODS iCommands to only use a single thread.")
	flag.BoolVar(&getOp, "get", false, "Retrieve files from iRODS. Use the source as a path in iRODS and the destination as a local directory.")
	flag.BoolVar(&mkdirOp, "mkdir", false, "Creates the directory in iRODS specified by -destination.")
	flag.BoolVar(&avuOp, "avu", false, "Associates an AVU with a file or directory specified by -destination.")
	flag.StringVar(&avuAttr, "attr", "", "Attribute portion of an AVU.")
	flag.StringVar(&avuVal, "value", "", "Value portion of an AVU.")
	flag.StringVar(&avuUnit, "unit", "", "Unit portion of an AVU,")
	flag.Parse()
}

/*
 * Validates the source variable.
 * 
 * If the source is ".", then the current working directory is used.
 * Validates that the source directory actually exists.
 */
func ValidateSource() (err os.Error) {
	if !getOp {
		if source == "." {
			pwd, wderr := os.Getwd()

			if wderr != nil {
				err = os.NewError("Error setting source directory: " + wderr.String())
			} else {
				source = pwd
			}
		} else {
			if !ftutils.Exists(source) {
				err = os.NewError("Source directory " + source + " does not exist.")
			}
		}
	}

	return err
}

/*
 * Validates the dest variable.
 * 
 * Validates that the destination directory is actually set.
 */
func ValidateDest() (err os.Error) {
	if getOp {
		if dest == "" || dest == "." {
			dest, err = os.Getwd()

			if err != nil {
				return err
			}
		}

		if !ftutils.Exists(dest) {
			err = os.NewError("Destination directory  " + dest + " does not exist.")
		} else {
			var destInfo *os.FileInfo
			destInfo, err = os.Stat(dest)

			if err == nil {
				if !destInfo.IsDirectory() {
					err = os.NewError("Destination " + dest + " is not a directory.")
				}
			}
		}

	} else {
		if dest == "" {
			err = os.NewError("Destination directory must be set.")
		}
	}

	return err
}

/*
 * Allows the caller to execute a command.
 *
 * Params:
 *     cmd - String containing the path to the executable.
 *     env - Slice of strings containing "key=value" environment variable settings.
 *     args - vararg containing strings that are the arguments to the executable.
 */
func Execute(cmd string, env []string, args ...string) (output string, err os.Error) {
	fmt.Println("Executing the following command: ")
	fmt.Print(cmd + " ")

	for _, a := range args {
		fmt.Print(a + " ")
	}

	fmt.Print("\n\n")

	cmdStruct := exec.Command(cmd, args...)
	cmdStruct.Env = env
	outputBytes, err := cmdStruct.CombinedOutput()

	if err != nil {
		err = os.NewError("Error executing command " + cmd + ":\n\t" + err.String())
	}

	buf := bytes.NewBuffer(outputBytes)
	output = buf.String()

	return output, err
}

/*
 * Validates the flags passed in by the user. Calls ValidateSource and ValidateDest.
 */
func ValidateFlags() {
	sourceErr := ValidateSource()

	if sourceErr != nil {
		fmt.Println(sourceErr)
		fmt.Println(sourceErr.String())
		os.Exit(1)
	}

	destErr := ValidateDest()

	if destErr != nil {
		fmt.Println(destErr.String())
		os.Exit(1)
	}
}

/*
 * Utility function used by ListDir(). Takes in a *os.FileInfo structure and a path,
 * returns the actual path pointed to by the symlink.
 */
func HandleSymlink(fip *os.FileInfo, fipPath string) (newFipPath string, err os.Error) {
	symPath, evalErr := filepath.EvalSymlinks(fipPath)

	if evalErr != nil {
		err = os.NewError("Error on EvalSymlinks for " + fipPath + ": " + evalErr.String())
	} else {
		var statErr os.Error
		fip, statErr = os.Stat(fipPath)

		if statErr != nil {
			err = os.NewError("Error os.Stat()'ing " + fipPath + ": " + statErr.String())
		} else {
			fipPath = symPath
		}
	}

	return fipPath, err
}

/*
 * Another utility function used by ListDir().
 */
func GetDirPathInfo(dirpath string) (dirPathInfo *os.FileInfo, err os.Error) {
	dirPathInfo, lstatErr := os.Lstat(dirpath)

	if lstatErr != nil {
		err = os.NewError("Error Lstat()'ing " + dirpath + ": " + lstatErr.String())
	}

	return dirPathInfo, err
}

/*
 * Generates a list of paths (strings) that exist under a given directory. Can optionally
 * filter out files and directories, and can also optionally recurse into sub-directories.
 */
func ListDir(dirpath string, listFiles bool, listDirs bool, recurse bool) (filePaths []string, err os.Error) {
	var dirPathInfo *os.FileInfo
	filePaths = make([]string, 0) //return value

	dirPathInfo, err = GetDirPathInfo(dirpath)

	if err != nil {
		return filePaths, err
	}

	if dirPathInfo.IsDirectory() {
		fileInfos, readErr := ioutil.ReadDir(dirpath)

		if readErr != nil {
			err = os.NewError("Error listing directory: " + readErr.String())
			return filePaths, err
		}

		for fi := 0; fi < len(fileInfos); fi++ {
			fip := fileInfos[fi]

			fipPath := path.Join(dirpath, fip.Name)

			if fip.IsSymlink() {
				fipPath, err = HandleSymlink(fip, fipPath)
			}

			if fip.IsDirectory() {
				if listDirs {
					filePaths = append(filePaths, fipPath)
				}

				if recurse {
					recursedPaths, recurseErr := ListDir(fipPath, listFiles, listDirs, recurse)

					if recurseErr != nil {
						err = recurseErr
					} else {
						filePaths = append(filePaths, recursedPaths...)
					}
				}
			} else {
				filePaths = append(filePaths, fipPath)
			}
		}
	}
	return filePaths, err
}

/*
 * Accepts the output of a ListDir() call along with a list of paths to exclude
 * and returns a new list of paths with all of the excluded paths filtered out.
 */
func ListDirFiltered(paths []string, excludePaths []string) []string {
	filteredPaths := make([]string, 0)

	for _, fpath := range paths {
		foundPath := false

		for _, excludePath := range excludePaths {
			if fpath == excludePath {
				foundPath = true
			}

			if string(excludePath[len(excludePath)-1]) == "/" && strings.HasPrefix(fpath, excludePath) {
				foundPath = true
			}
		}

		if !foundPath {
			filteredPaths = append(filteredPaths, fpath)
		}
	}

	return filteredPaths
}

/*
 * Takes in a list of paths and returns a list of absolute paths. Probably should
 * be renamed to something a bit more generic.
 */
func NormalizeExcludes(excludes []string) (normalizedExcludes []string, err os.Error) {
	normalizedExcludes = make([]string, 0)
	errOccurred := false
	errString := ""

	for _, epath := range excludes {
		normalizedPath := epath

		if !path.IsAbs(normalizedPath) {
			var absErr os.Error
			normalizedPath, absErr = filepath.Abs(normalizedPath)

			if absErr != nil {
				if !errOccurred {
					errOccurred = true
				}
				errString = errString + "Error getting absolute path for " + epath + ": " + absErr.String() + "\n"
			}
		}

		if epath != "" {
			normalizedExcludes = append(normalizedExcludes, normalizedPath)
		}
	}

	if errOccurred {
		err = os.NewError(errString)
	}

	return normalizedExcludes, err
}

func GenerateDestinationPaths(inputPaths []string, sourceDirPath string) (destPaths map[string]string, err os.Error) {
	destPaths = make(map[string]string)
	errOccurred := false
	errString := ""

	if string(sourceDirPath[len(sourceDirPath)-1]) != "/" {
		sourceDirPath = sourceDirPath + "/"
	}

	sourceDirPath, err = filepath.Abs(sourceDirPath)

	if err != nil {
		errOccurred = true
		errString = errString + "Error getting absolute path of " + sourceDirPath + ": " + err.String() + "\n"
	} else {
		for _, inputPath := range inputPaths {
			absSourcePath, absErr := filepath.Abs(inputPath)

			if absErr != nil {
				if !errOccurred {
					errOccurred = true
				}
				errString = errString + "Error getting absolute path of " + absSourcePath + ": " + absErr.String() + "\n"
			} else {
				relSourcePath := string(absSourcePath[len(sourceDirPath):])
				fmt.Println(relSourcePath)
				destPaths[inputPath] = relSourcePath
			}
		}
	}

	if errOccurred {
		err = os.NewError(errString)
	}

	return destPaths, err
}

func CreateExcludedFileList(delimiter string) (fileList []string, err os.Error) {
	fileList = strings.Split(excludeFilesRaw, delimiter)
	fileList, err = NormalizeExcludes(fileList)
	return fileList, err
}

func CreateIncludedFileList(delimiter string) (fileList []string, err os.Error) {
	includedFiles := strings.Split(includeFilesRaw, delimiter)
	fileList = make([]string, 0)
	errOccurred := false
	errString := ""

	for _, fpath := range includedFiles {
		if !ftutils.Exists(fpath) {
			errOccurred = true
			errString += "Included file " + fpath + " doesn't exist."
		}

		fstat, err := os.Stat(fpath)

		if err != nil {
			errOccurred = true
			errString += "Couldn't stat " + fpath + "."
		} else {
			if fstat.IsDirectory() {
				flist, err := ListDir(fpath, true, false, true)

				if err != nil {
					errOccurred = true
					errString += "Couldn't list " + fpath + "."
				} else {
					fileList = append(fileList, flist...)
				}
			} else {
				fileList = append(fileList, fpath)
			}
		}
	}

	if errOccurred {
		err = os.NewError(errString)
	}

	return fileList, err
}

func CreateSourceFileList() (sourceFileList []string, err os.Error) {
	sourceFileList = make([]string, 0)
	excludeFiles, err = CreateExcludedFileList(excludeFilesDelimiter)

	if err != nil {
		return sourceFileList, err
	}

	if includeFilesRaw != "" {
		sourceFileList, err = CreateIncludedFileList(includeFilesDelimiter)
	} else {
		sourceFileList, err = ListDir(source, true, false, true)
	}

	if err != nil {
		return sourceFileList, err
	}

	sourceFileList = ListDirFiltered(sourceFileList, excludeFiles)
	return sourceFileList, err
}

func GetIrodsDirectory() (irodsDir string, err os.Error) {
	irodsDir = ""
	userId := os.Getuid()
	userInfo, lookupErr := user.LookupId(userId)

	if lookupErr != nil {
		err = os.NewError(fmt.Sprintf("Error looking up user ID %d: "+err.String(), userId))
	} else {
		irodsDir = path.Join(userInfo.HomeDir, ".irods")
	}

	return irodsDir, err
}

func FindIrodsSettingsFiles() (retMap map[string]string, err os.Error) {
	retMap = make(map[string]string)

	irodsDir, rodsErr := GetIrodsDirectory()

	if rodsErr != nil {
		return retMap, rodsErr
	}

	if !ftutils.Exists(irodsDir) {
		err = os.NewError("Couldn't find .irods directory at " + irodsDir)
		return retMap, err
	}

	irodsAPath := path.Join(irodsDir, ".irodsA")

	if !ftutils.Exists(irodsAPath) {
		err = os.NewError("Couldn't find .irodsA file in " + irodsDir)
		return retMap, err
	}

	irodsEnvPath := path.Join(irodsDir, ".irodsEnv")

	if !ftutils.Exists(irodsEnvPath) {
		err = os.NewError("Couldn't find .irodsEnv file in " + irodsDir)
		return retMap, err
	}

	retMap["irodsAuthFileName"] = irodsAPath
	retMap["irodsEnvFile"] = irodsEnvPath
	return retMap, err
}

func ExitOnError(err os.Error) {
	if err != nil {
		fmt.Println(err.String())
		os.Exit(1)
	}
}

func doPut(imkdirPath string, ilsPath string, iputPath string, irodsEnv []string) {
	//Walks the source directory and generates a list of files
	//that need to get transferred to the destination.
	sourceFileList, err := CreateSourceFileList()
	ExitOnError(err)

	//Generate destination paths and associate them with the source paths.
	destPathsMap, err := GenerateDestinationPaths(sourceFileList, source)
	ExitOnError(err)
	fmt.Println(source)
	fmt.Println(destPathsMap)

	//Create the destination directory.
	mkdirOutput, mkdirErr := Execute(imkdirPath, irodsEnv, "-p", dest)
	ExitOnError(mkdirErr)
	fmt.Println(mkdirOutput)

	ilsOutput, ilsErr := Execute(ilsPath, irodsEnv, dest)
	ExitOnError(ilsErr)
	fmt.Println(ilsOutput)

	for sourcePath, destPath := range destPathsMap {
		destDir, _ := filepath.Split(destPath)

		if destDir != "" {
			newDir := path.Join(dest, destDir)
			mkdirOutput, mkdirErr = Execute(imkdirPath, irodsEnv, "-p", newDir)
			ExitOnError(mkdirErr)

			if mkdirOutput != "" {
				fmt.Print(mkdirOutput)
			}
		}

		var iputOutput string
		var iputErr os.Error
		var iputArgs []string

		fullDest := path.Join(dest, destPath)

		if singleThreaded {
			iputArgs = []string{"-f", "-P", "-N 0", sourcePath, fullDest}
		} else {
			iputArgs = []string{"-f", "-P", sourcePath, fullDest}
		}

		iputOutput, iputErr = Execute(iputPath, irodsEnv, iputArgs...)

		if iputErr != nil {
			fmt.Println(iputErr.String())
		}

		fmt.Print(iputOutput)
	}
}

func doGet(igetPath string, ilsPath string, irodsEnv []string) {
	var getDir bool
	var igetArgs []string
	var igetOutput string
	var igetErr os.Error

	//Are we retrieving a directory or a file?
	if string(source[len(source)-1]) == "/" {
		getDir = true
		source = string(source[:len(source)-1])
	} else {
		getDir = false
	}

	if singleThreaded {
		igetArgs = []string{"-f", "-P", "-N 0"}
	} else {
		igetArgs = []string{"-f", "-P"}
	}

	if getDir {
		igetArgs = append(igetArgs, "-r")
	}

	igetArgs = append(igetArgs, source, dest)

	igetOutput, igetErr = Execute(igetPath, irodsEnv, igetArgs...)

	if igetErr != nil {
		fmt.Println(igetErr.String())
	}

	fmt.Print(igetOutput)
}

func doMkdir(imkdirPath string, irodsEnv []string) {
	var imkdirArgs []string
	var imkdirOutput string
	var imkdirErr os.Error

	imkdirArgs = []string{"-p", dest}

	imkdirOutput, imkdirErr = Execute(imkdirPath, irodsEnv, imkdirArgs...)

	if imkdirErr != nil {
		fmt.Println(imkdirErr.String())
	}

	fmt.Print(imkdirOutput)
}

func doAvu(imetaPath string, irodsEnv []string, isFile bool) {
	var imetaArgs []string
	var imetaOutput string
	var imetaErr os.Error
	var typeFlag string

	if isFile {
		typeFlag = "-d"
	} else {
		typeFlag = "C"
	}

	imetaArgs = []string{"add", typeFlag, dest, avuAttr, avuVal, avuUnit}

	imetaOutput, imetaErr = Execute(imetaPath, irodsEnv, imetaArgs...)

	if imetaErr != nil {
		fmt.Println(imetaErr.String())
	}

	fmt.Print(imetaOutput)
}

func main() {
	SetupFlags()
	ValidateFlags()

	//Set up pathing for the executables and the config files
	//for the icommands.
	imkdirPath, err := exec.LookPath(IMKDIR)
	ExitOnError(err)

	iputPath, err := exec.LookPath(IPUT)
	ExitOnError(err)

	ilsPath, err := exec.LookPath(ILS)
	ExitOnError(err)

	igetPath, err := exec.LookPath(IGET)
	ExitOnError(err)

	imetaPath, err := exec.LookPath(IMETA)
	ExitOnError(err)

	icommandsFiles, settingsErr := FindIrodsSettingsFiles()
	ExitOnError(settingsErr)

	//Set up the environment variables for the icommands.
	irodsEnv := []string{
		"irodsAuthFileName=" + icommandsFiles["irodsAuthFileName"],
		"irodsEnvFile=" + icommandsFiles["irodsEnvFile"],
	}

	//Set up the clientUserName, if necessary.
	clientUser := os.Getenv("clientUserName")

	if clientUser != "" {
		irodsEnv = append(irodsEnv, "clientUserName="+clientUser)
	}

	if avuOp {
		doAvu(imetaPath, irodsEnv, true)
		fmt.Println("DONE!")
		os.Exit(0)
	}

	if mkdirOp {
		doMkdir(imkdirPath, irodsEnv)
		fmt.Println("DONE!")
		os.Exit(0)
	}

	if getOp {
		doGet(igetPath, ilsPath, irodsEnv)
		fmt.Println("DONE!")
		os.Exit(0)
	}

	doPut(imkdirPath, ilsPath, iputPath, irodsEnv)
	fmt.Println("DONE!")
}
