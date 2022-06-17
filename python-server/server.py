# Сервер, который работает с нейронной сетью. Получает данные и возвращает клиенту предсказание нейронной сети для этих данных

import socket
import PIL
import tensorflow as tf
import keras
from tensorflow.keras.models import load_model
from sklearn.model_selection import train_test_split
import cv2

import numpy as np
import matplotlib.pyplot as plt

from pathlib import Path
import glob

from PIL import Image
from PIL import ImageFilter
from PIL import ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True

from io import BytesIO, StringIO

import base64

image_path = r'C:\Users\Michael Korol\Downloads\recvimage.jpg'


def get_labels(data_folder_path):
    labels_list = []

    for dir_path in glob.glob(data_folder_path):
        img_label = dir_path.split('\\')[-1]
        labels_list.append(img_label)

    return labels_list


def make_prediction(model_to_predict, labels_list, img):
    img_class = model_to_predict.predict(img)

    print('img class: ', np.argmax(img_class))

    predicted_class = np.argmax(img_class)
    label_of_predicted_class = labels_list[np.argmax(img_class) - 1]

    return predicted_class, label_of_predicted_class


def image_to_predict(img_test):
    new_image = np.float32(img_test)
    new_image = cv2.resize(new_image, (28, 28))
    new_image = cv2.cvtColor(new_image, cv2.COLOR_RGB2BGR)
    new_image = cv2.normalize(new_image, None, alpha=0, beta=1, norm_type=cv2.NORM_MINMAX, dtype=cv2.CV_32F)
    new_image = np.array(new_image)
    
    #plt.imshow(new_image)
    #plt.show()

    img = (np.expand_dims(new_image, 0))  # ready to be use in model_to_predict

    return img


def model_work(data):
    labels_list = get_labels(r'C:\Users\Michael Korol\Documents\diploma\Braille_set\Test\*') # путь до тестового датасета на компе
    labels_list.sort()

    model_to_predict = load_model(r'C:\Users\Michael Korol\Documents\Diploma\final_diploma_model\\') # путь до файла с нейронкой

    img = cv2.imread(image_path, cv2.IMREAD_GRAYSCALE)

    plt.imshow(img)
    plt.show()

    image_ = image_to_predict(img)

    predicted_class, label_of_predicted_class = make_prediction(model_to_predict, labels_list, image_)

    return predicted_class, label_of_predicted_class


print ("I am here before socket")

if __name__ == "__main__":
    port = 5067
    print ("I am here before server socket")
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    print ("I am here after server socket")
    server_socket.bind(('', port))
    server_socket.listen(10)

    print('server is listening on port ', port, '; press ctrl+c to interrupt')
    

    while True:
        print ("I am here after while true")
        client_socket, client_address = server_socket.accept()
        print ("I am here after server socket accept")
        print('connection from: ', client_address)

        #img_size = client_socket.recv(4096).decode()

        img_size = int.from_bytes(client_socket.recv(4), "big")
        print("image size: ", img_size)

        #data = client_socket.recvall(img_size)

        data = bytearray()

        while len(data) < img_size:
            packet = client_socket.recv(img_size - len(data))
            if not packet:
                break
            data.extend(packet)

        print("data received. ")
        print("received data size: ", len(data))

        f = open(image_path, 'wb')
        f.write(data)
        f.close()

        #client_socket.send('making a prediction... \r\n'.encode())

        predicted_class, label_of_predicted_class = model_work(data)

        print('prediction: ', predicted_class, ' ', label_of_predicted_class)

        client_socket.close()

        client_socket, client_address = server_socket.accept()

        print('connection from: ', client_address)

        client_socket.send(str(label_of_predicted_class).encode())

        client_socket.close()

        

