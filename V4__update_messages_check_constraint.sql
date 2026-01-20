-- Drop the restrictive check constraint on message_type
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;

-- Add new check constraint with CHAT_ROOM
ALTER TABLE messages ADD CONSTRAINT messages_message_type_check 
    CHECK (message_type IN ('PLAZA', 'LOCAL_ROOM', 'DM', 'CHAT_ROOM'));

-- Drop the complex logic constraint as it doesn't account for CHAT_ROOM and chat_room_id
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_check;
