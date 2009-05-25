#!/usr/bin/ruby

require 'net/smtp'

smtp = Net::SMTP.new('localhost', 10025)
smtp.esmtp = false
smtp.set_debug_output $stderr
smtp.start("localhost") do |smtp|
  smtp.send_message "hello!", 'your@mail.address', 'his_addess@example.com'
end
