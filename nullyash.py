import configParser
from datetime import datetime
import os
import time

config = configparser.ConfigParser(interpolation=None)
config.read('config.ini')
today = datetime.now().strftime("%d%m%Y")
file_date = config.get['section_b']['today']#print(file_date)
config.set('section_b', 'date', today)
with open('config.ini', 'w') as configfile:
    config.write(configfile)

config_file_list = config['files']['Xml_files_list']
my_list = config_file_list.split(",")         # config files list
file_path = config['section_a']['mdm_mitexch_out_path_1']   # folder file list
# os.chdir(file_path)
dir_file_list = os.listdir(file_path)
print(dir_file_list)

#print(my_list)


# config_files_list = []
# for filename in file_list:
#     if '\n' in filename:
#         filename = filename.strip()
#         config_files_list.append(filename)
#     else:
#         config_files_list.append(filename)
#
# print(config_files_list)

filefound = []
count = 0
final_count = len(my_list)
print(final_count)

while count != final_count:
    print('Waiting for files')
    for filename in dir_file_list:
        if filename not in filefound: # continuously not reading same file
        #if '\n' in filename:
        #    filename = filename.strip()
            if filename in dir_file_list:
                # filename = filename.lstrip()
                # print(file_path+filename+'.xml')
                filefound.append(filename)
                print(filename, ' found')
                count += 1

                # elif os.path.exists(filename):
                #     filefound.append(filename)
                #     print(filename, ' found')
                #     count += 1
                #
                # elif os.path.exists(file_path + filename + today + '.csv'):
                #     filefound.append(filename+today)
                #     print(filename+ today, ' found')
                #     count += 1
    print(len(filefound))
    time.sleep(10)


