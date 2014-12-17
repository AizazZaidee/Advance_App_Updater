Advance App Updater
===================


Update your App without any Play Store. 

> **Code:** 

    UpdateCheck.getInstance().checkForUpdate(getApplicationContext(),"http://YourServer",true,new UpdateCheck.UpdateCheckCallback() {
                        @Override
                        public void noUpdateAvailable() {
                        System.out.println("No Update Available");
                        }
                        
                        @Override
                        public void onUpdateAvailable() {
                            System.out.println("Update Available");
                        }
                    });

See [MainActivity.java](https://github.com/AizazAZ/Advance_App_Updater/blob/master/app/src/main/java/com/az/advance/app/updater/MainActivity.java) for more detail.

> - **Required Json** 
> { "updateURL" : "http://www.path-to-apk/app.apk",
  "versionCode" : "1"
}


----------
> - **Notification 1** 
![Step 1](https://raw.githubusercontent.com/AizazAZ/Advance_App_Updater/master/app/src/main/res/drawable/1.png)

> - **Notification 2** 
![Step 2](https://raw.githubusercontent.com/AizazAZ/Advance_App_Updater/master/app/src/main/res/drawable/2.png)

> - **Notification 3** 
![Step 3](https://raw.githubusercontent.com/AizazAZ/Advance_App_Updater/master/app/src/main/res/drawable/3.png)

