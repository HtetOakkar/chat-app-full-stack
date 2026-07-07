async function displayNotification(
  title: string,
  options?: NotificationOptions
): Promise<void> {
  try {
    new Notification(title, options);
  } catch (error) {
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
      try {
        let registration = await navigator.serviceWorker.getRegistration();
        if (!registration && navigator.serviceWorker.ready) {
          registration = await Promise.race([
            navigator.serviceWorker.ready,
            new Promise<undefined>((resolve) =>
              setTimeout(() => resolve(undefined), 1500)
            ),
          ]);
        }
        if (
          registration &&
          typeof registration.showNotification === "function"
        ) {
          await registration.showNotification(title, options);
        }
      } catch (swError) {
        console.error(
          "Failed to display notification via ServiceWorker:",
          swError
        );
      }
    }
  }
}

export async function showNotification(
  title: string,
  options?: NotificationOptions
): Promise<void> {
  if (typeof Notification === "undefined") {
    return;
  }
  let permission = Notification.permission;
  if (permission !== "granted" && permission !== "denied") {
    try {
      permission = await Notification.requestPermission();
    } catch (error) {
      console.error("Failed to request notification permission:", error);
      return;
    }
  }
  if (permission === "granted") {
    await displayNotification(title, options);
  }
}
