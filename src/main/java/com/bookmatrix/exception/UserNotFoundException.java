package com.bookmatrix.exception;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(long userId) {
        super("User " + userId + " was not found.");
    }

    @Override public String code() { return "USER_NOT_FOUND"; }
}
