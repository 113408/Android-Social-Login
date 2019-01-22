# Implementing Social login in your Android without using SDKs

Every good app needs good UI/UX. Having a good user experience means make it easy and fast for people to get into and around your app. If your app needs to authenticate users, either to send data to your backend or to customize it accordingly to the person using it, you might find yourself creating some big architecture behind the scene to accommodate that. It takes time and can also be frustrating for the user as they might not feel like getting though the whole process of registration -> confirmation -> activation.

Hopefully there is an alternative for doing this. We can ask the users to authenticate using their social media accounts, this will guarantee that the user has been already verified and most probably it's a legit user.

[This tutorial](https://medium.com/@113408/implementing-social-login-in-android-without-sdks-64a1c49e0bfd) covers how to achieve this authentication using 3 of the most popular social medias i.e: Google, Facebook and Twitter.

> NB: There is a different way of doing this authentication, you can use the AccountManager Android class, this will allow you to authenticate the users with apps that they already have on their phone. However if the users don't have, for example Facebook App on then you will not be able to authenticate them using Facebook

