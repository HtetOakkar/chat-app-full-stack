# Contacts & Search API Documentation

This document outlines the API endpoints and architectural design for the newly implemented **User Search** and **Address Book Contacts** features. It is intended for the Design and Frontend Development teams to integrate these features into the UI.

---

## 1. Architectural Overview

The backend uses Spring Boot to provide RESTful APIs for user discovery and contact management. 
- **User Search:** An open database search for usernames.
- **Address Book (Contacts):** A one-way list that allows an owner to save specific users to their contacts file (similar to Telegram). No mutual consent or friend request logic is required.

---

## 2. API Endpoints

All endpoints require the user to be authenticated via a JWT token in the `Authorization: Bearer <token>` header.

### 2.1 Search Users

Searches for users by a partial or full username. Results do not include sensitive data like passwords.

- **Endpoint:** `GET /api/v1/users/search`
- **Query Parameters:**
  - `keyword` (string, required): The search string (e.g., `?keyword=john`).
- **Response (200 OK):**
  ```json
  [
    {
      "id": 101,
      "username": "johndoe",
      "createdAt": "2026-03-24T10:00:00Z",
      "updatedAt": "2026-03-24T10:00:00Z"
    },
    {
      "id": 105,
      "username": "johnsmith",
      "createdAt": "2026-03-24T10:05:00Z",
      "updatedAt": "2026-03-24T10:05:00Z"
    }
  ]
  ```

---

### 2.2 Add to Contacts

Saves a user to the authenticated user's address book.

- **Endpoint:** `POST /api/v1/contacts`
- **Request Body:**
  ```json
  {
    "username": "johndoe"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "id": 1,
    "contactUserId": 101,
    "contactUsername": "johndoe",
    "createdAt": "2026-03-24T11:00:00Z"
  }
  ```
- **Error Codes:**
  - `400 Bad Request`: If the user attempts to add themselves.
  - `404 Not Found`: If the requested username does not exist.
  - `409 Conflict`: If the user is already in the contact list.

---

### 2.3 Get Contacts

Retrieves the authenticated user's saved contacts.

- **Endpoint:** `GET /api/v1/contacts`
- **Response (200 OK):**
  ```json
  [
    {
      "id": 1,
      "contactUserId": 101,
      "contactUsername": "johndoe",
      "createdAt": "2026-03-24T11:00:00Z"
    }
  ]
  ```

---

### 2.4 Remove Contact

Deletes a user from the authenticated user's address book.

- **Endpoint:** `DELETE /api/v1/contacts/{contactId}`
- **Path Parameters:**
  - `contactId` (number, required): The ID of the contact user to remove (This maps to `contactUserId`).
- **Response (204 No Content):** (No body returned)
- **Error Codes:**
  - `404 Not Found`: If the contact is not in the address book.

---

## 3. Design Team Guidelines

When building the UI for these features, please consider the following user flows:

1. **Global Search Bar:**
   - A search bar should exist prominently allowing users to type a query.
   - Use debouncing on the frontend before calling `GET /api/v1/users/search?keyword={query}`.
   - Users should be able to click on a search result to immediately open a chat screen with that user.

2. **Address Book Screen:**
   - Display the list of contacts using `GET /api/v1/contacts`.
   - Each contact card should have a "Message" button and an "Options/Remove" button triggering `DELETE /api/v1/contacts/{contactId}`.

3. **Add Contact Flow:**
   - When inside a chat with a user who is **not** currently in the address book, display a prominent "Add to Contacts" button.
   - On click, send a `POST /api/v1/contacts` request with their username.
