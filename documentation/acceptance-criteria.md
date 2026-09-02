# Acceptance Criteria

## Epic 1: Session Management

### US-01 – Create a Gaming Session

- Given I am logged in, when I provide a game, valid date/time and player limit, then I can create a session.
- The created session is saved and visible to me.
- Required fields must be completed before the session can be created.
- The player limit must be a valid positive number.

### US-02 – Search for and Select a Game

- A list of available games is displayed when selecting a game.
- I can search for a game by name.
- Matching search results are displayed.
- I can select one game for the session.

### US-03 – Invite Friends or Groups

- I can select one or more friends when creating or editing a session.
- I can select a friend group instead of selecting each member individually.
- Invited users receive a session invitation.
- The same user is not invited multiple times if they belong to multiple selected groups.

### US-04 – Create an Open Lobby

- I can configure a session to allow eligible users to join without an individual invitation.
- Eligible users can see the open session.
- Eligible users can join while spaces remain.
- Users who are not eligible cannot join the session.

### US-05 – Respond to or Leave a Session

- I can accept or decline a session invitation.
- I can join an eligible open session.
- I can leave a session I previously joined.
- My participation status is updated after each action.
- The organiser can see the updated participation status.

### US-06 – Manage a Created Session

- I can view the current list of participants.
- I can edit session details before the session occurs.
- I can cancel a session I created.
- Participants are notified when significant session details change or the session is cancelled.
- Users other than the organiser cannot edit or cancel the session.

### US-07 – Online or In-Person Session

- I can choose either online or in person when creating or editing a session.
- The selected session type is visible in the session details.
- The session cannot have an invalid or unsupported session type.

### US-08 – In-Person Location

- When a session is in person, I can provide a location.
- The location is displayed to eligible participants.
- An online session does not require an in-person location.
- The organiser can update the location before the session.

### US-09 – Full Lobbies

- The application tracks the number of confirmed participants.
- Once the player limit is reached, additional users cannot join.
- The session is displayed as full when capacity has been reached.
- If a participant leaves, a space becomes available again.

### US-10 – NFC Session Invitation

- I can initiate NFC sharing from a session.
- A compatible nearby device can receive the session invitation.
- Receiving the NFC invitation opens or displays the correct session.
- The receiving user must still satisfy the session's joining and capacity rules.
- An invalid NFC payload does not cause the user to join a session.

### US-11 – Session Visibility

- I can select a supported visibility option when creating or editing a session.
- Only users allowed by the selected visibility can discover the session.
- Private sessions are accessible only to invited users.
- Group-only sessions are accessible only to eligible group members.
- Visibility changes are applied to future access to the session.

### US-12 – Session Notes

- I can add optional notes when creating or editing a session.
- Notes are visible within the session details.
- The organiser can edit or remove the notes.

### US-13 – View Session Details

- The session detail view shows the game, date, time and player capacity.
- It shows whether the session is online or in person.
- Where applicable, it displays the location and session notes.
- It displays the organiser and current participants.
- The information shown reflects the latest session details.

### US-14 – Pending Session Invitations

- I can view my pending session invitations in one location.
- Each invitation identifies the relevant session.
- I can accept or decline an invitation from the invitation view.
- Responded-to invitations are no longer shown as pending.

---

## Epic 2: Calendar & Scheduling

### US-15 – Gaming Calendar

- Sessions I organise or attend appear on my calendar.
- Sessions are displayed on their correct dates and times.
- Selecting a calendar entry opens its session details.
- Cancelled sessions are either removed or clearly identified as cancelled.

### US-16 – Import Phone Calendar

- I can grant the application access to my device calendar.
- Existing calendar commitments can be recognised as unavailable periods.
- The application continues to function if calendar permission is denied.
- Imported commitments do not become gaming sessions.

### US-17 – Scheduling Conflicts

- The application checks the proposed session time against known calendar commitments.
- A warning is displayed when a conflict is detected.
- The conflicting period is clearly indicated.
- A scheduling warning does not incorrectly appear when there is no overlap.

### US-18 – Session Notifications

- I receive a notification or reminder before an upcoming session.
- Participants are notified when important session details change.
- Participants are notified if a session is cancelled.
- Notifications identify the relevant session.

---

## Epic 3: Friends & Groups

### US-19 – Friend Management

- I can view my current friends.
- I can initiate adding another user as a friend.
- I can remove an existing friend.
- Removed users no longer appear in my friends list.

### US-20 – Friend Groups

- I can create a named friend group.
- I can add friends to a group.
- I can remove friends from a group.
- I can edit or delete a group I manage.
- Groups can be selected when inviting users to a session.

### US-21 – Add Friend Using NFC

- I can initiate an NFC friend exchange.
- A compatible nearby device can receive my user information.
- The receiving user can initiate a friend request from the NFC exchange.
- The correct user account is identified.
- NFC interaction does not automatically create a friendship without confirmation.

### US-22 – Add Friend Using QR Code

- A user can display a QR code representing their profile.
- Another user can scan the QR code using the device camera.
- A valid QR code identifies the correct user.
- The scanning user can initiate a friend request.
- Invalid or unrelated QR codes are handled without adding a user.

### US-23 – Friend Requests

- I can view my pending friend requests.
- I can accept a pending request.
- Accepting a request adds the users as friends.
- I can decline a pending request.
- Declining a request does not add the requesting user as a friend.

---

## Epic 4: QR Invitations & Security

### US-24 – Generate a Session QR Invitation

- I can generate a QR code for a session I organise.
- The QR code uniquely identifies the intended session.
- The QR code can be displayed for another user to scan.
- The QR code does not bypass session visibility or capacity rules.

### US-25 – Scan a Session QR Code

- I can use the device camera to scan a session QR code.
- A valid QR code opens the correct session details.
- If eligible and capacity remains, I can join the session.
- A full or restricted session cannot be joined through the QR code.
- Invalid QR codes display an appropriate error.

### US-26 – Biometric Authentication

- If the device supports biometric authentication, I can use it to unlock the application.
- Successful authentication grants access.
- Failed authentication does not grant access.
- The application handles devices without supported biometrics appropriately.
- The application provides an alternative authentication method where required.

---

## Epic 5: Local Event Discovery

### US-27 – Discover Nearby Gaming Events

- With location access enabled, the application can determine my approximate location.
- Nearby gaming events are displayed on a map.
- Each event marker can be selected to view event information.
- Event information includes at least the event name, time and location.
- The application handles unavailable or denied location access appropriately.

### US-28 – Sign Up for a Local Gaming Event

- I can sign up for an eligible event.
- My attendance is recorded after successful signup.
- I can see that I am registered for the event.
- I cannot accidentally register for the same event multiple times.
- Registration is prevented when the event cannot accept additional attendees.

---

## Epic 6: Account & Onboarding

### US-29 – Account Creation and Profile Setup

- A new user can create an account using the required credentials.
- Invalid or incomplete registration information is rejected with an appropriate message.
- A successfully registered user can log in.
- The user can set up basic profile information such as a display name.
- The user's profile is saved and can be viewed after onboarding.
