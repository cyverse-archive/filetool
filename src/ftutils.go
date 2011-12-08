package ftutils

import "os"

func Exists(filepath string) bool {
	retval := true
	_, err := os.Stat(filepath)

	if err != nil {
		retval = false
	}

	return retval
}
