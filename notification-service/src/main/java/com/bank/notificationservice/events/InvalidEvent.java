package com.bank.notificationservice.events;
public class InvalidEvent extends RuntimeException {
    public InvalidEvent(String detail) {super(detail);}
}
