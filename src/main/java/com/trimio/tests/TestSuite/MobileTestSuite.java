package com.trimio.tests.TestSuite;
import com.trimio.tests.*;
import com.trimio.tests.AppointmentFlow.TrimioScheduleAppointmentTest;
import com.trimio.tests.Base.AppiumBase;
import com.trimio.tests.Login.TrimioLoginTest;

public class MobileTestSuite {
    TrimioLoginTest loginTest = new TrimioLoginTest();
    TrimioScheduleAppointmentTest appointmentTest = new TrimioScheduleAppointmentTest();

    public static void main(String[] args) {

    }
}
