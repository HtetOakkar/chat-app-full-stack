/* eslint-disable @typescript-eslint/no-require-imports */
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const dashboardPath = path.join(__dirname, '../src/components/ChatDashboard.tsx');
const viewportPath = path.join(__dirname, '../src/components/ChatViewport.tsx');
const sidebarPath = path.join(__dirname, '../src/components/Sidebar.tsx');
const layoutPath = path.join(__dirname, '../src/app/layout.tsx');
const authContainerPath = path.join(__dirname, '../src/components/AuthContainer.tsx');
const userProfilePath = path.join(__dirname, '../src/components/UserProfileModal.tsx');

try {
    console.log('Running Mobile UI & Rebranding static verification tests...');
    
    const dashboardContent = fs.readFileSync(dashboardPath, 'utf8');
    assert(dashboardContent.includes('onBackToList'), 'ChatDashboard must pass onBackToList to ChatViewport');
    assert(dashboardContent.includes('activeChat === null') && dashboardContent.includes('"hidden md:flex" : "flex"'), 'ChatDashboard must contain conditional mobile layout classes');

    const viewportContent = fs.readFileSync(viewportPath, 'utf8');
    assert(viewportContent.includes('arrow_back'), 'ChatViewport must render a back button with arrow_back icon');
    assert(viewportContent.includes('onBackToList'), 'ChatViewport must support the onBackToList prop');

    const sidebarContent = fs.readFileSync(sidebarPath, 'utf8');
    assert(sidebarContent.includes('activeChat === null ? "flex w-full" : "hidden"'), 'Sidebar must contain mobile responsive layout classes');

    // Rebranding checks: Ensure "The Archivist" is renamed to "Meow Chit Chat" in source files
    const layoutContent = fs.readFileSync(layoutPath, 'utf8');
    const authContainerContent = fs.readFileSync(authContainerPath, 'utf8');

    assert(!layoutContent.includes('The Archivist'), 'layout.tsx must not contain "The Archivist"');
    assert(layoutContent.includes('Meow Chit Chat'), 'layout.tsx must contain "Meow Chit Chat"');

    assert(!authContainerContent.includes('The Archivist'), 'AuthContainer.tsx must not contain "The Archivist"');
    assert(authContainerContent.includes('Meow Chit Chat'), 'AuthContainer.tsx must contain "Meow Chit Chat"');

    assert(!sidebarContent.includes('The Archivist'), 'Sidebar.tsx must not contain "The Archivist"');
    assert(sidebarContent.includes('Meow Chit Chat'), 'Sidebar.tsx must contain "Meow Chit Chat"');

    // Auth Container Scroll Fix Check
    assert(authContainerContent.includes('overflow-y-auto'), 'AuthContainer.tsx must use overflow-y-auto to allow scrolling on small screens');
    assert(authContainerContent.includes('max-h-'), 'AuthContainer.tsx must limit the height of the card to enable scrolling');

    // Auth Container Password Visibility & Confirm Password checks
    assert(authContainerContent.includes('showPassword'), 'AuthContainer.tsx must contain showPassword state');
    assert(authContainerContent.includes('visibility'), 'AuthContainer.tsx must use visibility icon for show password');
    assert(authContainerContent.includes('visibility_off'), 'AuthContainer.tsx must use visibility_off icon for hide password');
    assert(authContainerContent.includes('confirmPassword'), 'AuthContainer.tsx must contain confirmPassword state for registration form');
    assert(authContainerContent.includes('showConfirmPassword'), 'AuthContainer.tsx must contain showConfirmPassword state for registration form');

    // Sidebar Requests Toggle checks
    assert(sidebarContent.includes('sidebarView'), 'Sidebar.tsx must contain sidebarView state');
    assert(sidebarContent.includes('setSidebarView'), 'Sidebar.tsx must support toggling sidebarView');

    // Real-time Requests count updates checks
    const websocketPath = path.join(__dirname, '../src/context/WebSocketContext.tsx');
    const websocketContent = fs.readFileSync(websocketPath, 'utf8');

    assert(websocketContent.includes('contacts:updated'), 'WebSocketContext.tsx must dispatch "contacts:updated" event on incoming requests');
    assert(sidebarContent.includes('contacts:updated'), 'Sidebar.tsx must listen to "contacts:updated" event');

    // State clearing and userId dependency in WebSocketContext.tsx checks
    assert(websocketContent.includes('setPublicMessages([])'), 'WebSocketContext.tsx must clear publicMessages on logout');
    assert(websocketContent.includes('setPrivateMessages({})'), 'WebSocketContext.tsx must clear privateMessages on logout');
    assert(websocketContent.includes('setOnlineUsers({})'), 'WebSocketContext.tsx must clear onlineUsers on logout');
    assert(websocketContent.includes('setHasMorePublicHistory(true)'), 'WebSocketContext.tsx must reset hasMorePublicHistory on logout');
    assert(websocketContent.includes('setHasMorePrivateHistory({})'), 'WebSocketContext.tsx must reset hasMorePrivateHistory on logout');
    assert(websocketContent.includes('[token, isAuthenticated, userId]'), 'WebSocketContext.tsx connection effect must depend on token, isAuthenticated, and userId');

    // Message deduplication checks in WebSocketContext.tsx
    assert(websocketContent.includes('new Date(x.timestamp).getTime() === new Date(m.timestamp).getTime()'), 'WebSocketContext.tsx must compare timestamps to deduplicate messages in history');

    // Header Full Name and Logout Confirmation checks
    assert(dashboardContent.includes('profile?.fullName'), "ChatDashboard.tsx must fetch and display the user's full name");
    assert(dashboardContent.includes('showLogoutConfirm'), 'ChatDashboard.tsx must contain showLogoutConfirm state for sign out confirmation');

    // TDD Search results redesign and profile card checks
    const userProfilePathCard = path.join(__dirname, '../src/components/UserProfileCard.tsx');
    assert(fs.existsSync(userProfilePathCard), 'UserProfileCard.tsx must exist');
    const userProfileCardContent = fs.readFileSync(userProfilePathCard, 'utf8');

    // 1. Sidebar must not contain ID: user.id
    assert(!sidebarContent.includes('ID: {user.id}'), 'Sidebar.tsx must not display the User ID in search results');

    // 2. Sidebar must not contain the inline Add button calling handleAddContact directly in search results
    assert(!sidebarContent.includes('handleAddContact(user.username, user.id)'), 'Sidebar.tsx must not contain the inline Add button in search results');

    // 3. Sidebar must track selectedProfileUser state to display profile card when a search result is clicked
    assert(sidebarContent.includes('selectedProfileUser') || dashboardContent.includes('selectedProfileUser'), 'Must track selectedProfileUser state');

    // 4. UserProfileCard must support conditional relationship action buttons (Add, Chat, Connect)
    assert(userProfileCardContent.includes('onAddContact'), 'UserProfileCard must support onAddContact prop/action');
    assert(userProfileCardContent.includes('onAcceptRequest'), 'UserProfileCard must support onAcceptRequest prop/action');
    assert(userProfileCardContent.includes('onSelectChat'), 'UserProfileCard must support onSelectChat prop/action');
    assert(userProfileCardContent.includes('userStatus'), 'UserProfileCard must support userStatus prop to handle externally provided status');

    // 4.5. ChatDashboard or Viewport must pass userStatus to UserProfileCard
    assert(viewportContent.includes('userStatus=') || dashboardContent.includes('userStatus='), 'Must pass userStatus to UserProfileCard to fix add+ button bug');

    // Ensure UserProfileModal is removed
    assert(!fs.existsSync(userProfilePath), 'UserProfileModal.tsx must be removed');


    // 5. ChatViewport must support date separation/grouping
    assert(viewportContent.includes('toDateString()'), 'ChatViewport.tsx must check message dates by comparing toDateString() values');
    assert(viewportContent.includes('formatDateHeader'), 'ChatViewport.tsx must implement or use a formatDateHeader function/helper');
    assert(viewportContent.includes('Today') && viewportContent.includes('Yesterday'), 'ChatViewport.tsx must format date headers with Today and Yesterday strings');

    // 6. Sidebar must check lastMessageSenderId and prepend "You: " if it matches the current user
    assert(sidebarContent.includes('lastMessageSenderId') && sidebarContent.includes('currentUserId'), 'Sidebar.tsx must check lastMessageSenderId against currentUserId');
    assert(sidebarContent.includes('You:'), 'Sidebar.tsx must prepend "You: " prefix to the last message if sent by the current user');

    // 7. Header visible on mobile and larger on mobile
    assert(!dashboardContent.match(/<header[^>]*className="[^"]*h-0[^"]*"/), 'ChatDashboard.tsx header must not have h-0 (hidden on mobile)');
    assert(dashboardContent.match(/<header[^>]*className="[^"]*(py-3|py-4)[^"]*"/), 'ChatDashboard.tsx header must have larger vertical padding (e.g. py-3 or py-4) for mobile view');
    assert(dashboardContent.match(/<header[^>]*className="[^"]*z-(20|30|40|50)[^"]*"/), 'ChatDashboard.tsx header must have z-index class of at least z-20 to stack above conversation header');

    // 8. Main View state
    assert(dashboardContent.includes('viewMode'), 'ChatDashboard.tsx must contain viewMode state to toggle between chat and profile');

    // 9. User Dropdown Menu
    assert(dashboardContent.includes('isUserMenuOpen'), 'ChatDashboard.tsx must contain isUserMenuOpen state for the user dropdown');
    assert(dashboardContent.includes('View Profile'), 'ChatDashboard.tsx dropdown must contain "View Profile" option');
    assert(dashboardContent.includes('Logout') && !dashboardContent.includes('<span className="hidden lg:block">Sign Out</span>'), 'ChatDashboard.tsx must have Logout in dropdown and remove standalone Sign Out button');

    // 10. MyProfileCard and View Mode Switching
    const myProfileCardPath = path.join(__dirname, '../src/components/MyProfileCard.tsx');
    assert(fs.existsSync(myProfileCardPath), 'MyProfileCard.tsx must exist');
    const myProfileCardContent = fs.readFileSync(myProfileCardPath, 'utf8');
    assert(myProfileCardContent.includes('arrow_back'), 'MyProfileCard.tsx must render a back button with arrow_back icon');
    
    assert(dashboardContent.includes('viewMode === \'profile\''), 'ChatDashboard.tsx must render MyProfileCard when viewMode is profile');
    assert(dashboardContent.includes('MyProfileCard'), 'ChatDashboard.tsx must import and use MyProfileCard');
    assert(!fs.existsSync(path.join(__dirname, '../src/components/MyProfileModal.tsx')), 'MyProfileModal.tsx must be removed');

    // Message request reply behavior checks
    assert(viewportContent.includes('PENDING_REQUEST') && viewportContent.includes('NEGLECTED') && viewportContent.includes('automatically accept'), 'ChatViewport.tsx must display a warning when replying to a pending/neglected request');
    assert(viewportContent.includes('onBannerAction()'), 'ChatViewport.tsx must call onBannerAction() upon replying to a request');
    assert(viewportContent.includes('/accept') && viewportContent.includes('handleSendMessage'), 'ChatViewport.tsx must call accept API on handleSendMessage for pending/neglected requests');
    assert(sidebarContent.includes('activeChat.status !== "PENDING_REQUEST"') && sidebarContent.includes('setSidebarView("chats")'), 'Sidebar.tsx must auto-switch view back to chats when activeChat is accepted');
    assert(sidebarContent.includes('prevActiveChatRef') && sidebarContent.includes('prevActiveChatRef.current'), 'Sidebar.tsx must use prevActiveChatRef to track activeChat transitions');

    console.log('Mobile UI & Rebranding verification tests passed successfully!');
    process.exit(0);
} catch (err) {
    console.error('Static verification test failed:');
    console.error(err.message);
    process.exit(1);
}

