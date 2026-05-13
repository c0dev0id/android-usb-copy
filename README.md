# Android Multi USB Copy Tool

A USB Hub is connected to an Android device. To the USB Hub, there is one big hard drive connected, and multiple SD Card Readers.
The tool will let the user select folders on source devices (sd card readers) and a folder on the destination device (big hard drive).

Once the folders are set up, the user can click "Start Transfer", which will start parallel transfers from all source devices to the destination device.
The reason behind this is that the source devices are usually a lot slower than the destination drive.
The user has the option to activate "Transfer Sequentially", which will process one transfer task after the other.
The user has the option to choose an overwrite strategy. He can pick between: Skip, Overwrite, Overwrite Smaller, Ask.
The user has the option to choose an error strategy. He can pick between: "Skip and continue" and "Stop transfer".
There will be a progress bar for each transfer.

In the destination folder, there is a folder created for each source device using the device UUID.

Example:

User selects source devices + folders:
/mnt/media_rw/AE1B-DFEA/DCIM
/mnt/media_rw/B2CD-CD21/DCIM
/mnt/media_rw/C1EF-D3FE/Camera1/DCIM
/mnt/media_rw/D31C-C4B1/DCIM

User selects destination device and folder:
/mnt/media_rw/F2F1-D13A/Backup

When "Start Transfer" is clicked, the following copy tasks will be started in parallel:

Task 1:
Copy /mnt/media_rw/AE1B-DFEA/DCIM/* to /mnt/media_rw/F2F1-D13A/Backup/AE1B-DFEA/

Task 2:
Copy /mnt/media_rw/B2CD-CD21/DCIM/* to /mnt/media_rw/F2F1-D13A/Backup/B2CD-CD21/

Task 3:
Copy /mnt/media_rw/C1EF-D3FE/Camera1/DCIM/* to /mnt/media_rw/F2F1-D13A/Backup/C1EF-D3FE/

Task 4:
Copy /mnt/media_rw/D31C-C4B1/DCIM/* to /mnt/media_rw/F2F1-D13A/Backup/D31C-C4B1/
                                    
On the main screen, each transfer tasks shows the following information:
- progress bar
- copy speed in MB/s
- in progress filename

The progress bar always relates to the total bytes on the source device.
If a transfer is interrupted at 45% and is then later continued, the skipped files will be added to the progress bar. So it jumps forward to 45% where the actual transfer resumes ("Skip" or "Overwrite Smaller" case).

When the user clicks on one of the task cards, a detail screen opens. The detail screen shows a scrollable list that shows in chronological transfer order
- every file that has been transferred in green with the file size.
- the file that's in progress with transfer speed and remaining MB in orange
- every file that encountered an error in red and the remark "Error"
- every file that was skipped in green and the remark "Skipped"


